import * as _ from 'lodash';
import * as ioredis from 'ioredis';

import bluebird from 'bluebird';

const debug = require('debug')('sched:verbose');
const debugInfo = require('debug')('sched')

import { load_scraper_redis, load_config } from '../lib/config';
import { DB, DBConfig, close_db, load_db } from '../lib/db';

export const config: any = load_config();
export const rdb: ioredis.Redis = load_scraper_redis(config);

const RDB_JOBS = 'jobs';
const RDB_RESOURCES = 'resources';
const RDB_FAILED_SEGMENTS = 'failedSegments';

const DUST_NEXT_EVENT = 25;

const MIN_BACKOFF = 1000;
const MAX_BACKOFF = 60 * 60 * 1000;

const MAX_CONCURRENT = 3;

const RUNNING_WAIT = 10 * 1000; // 10 seconds

// critical section for starting a job
const LUA_MARK_START_JOB = `
local function split(inputstr)
  local t={}
  for str in string.gmatch(inputstr, "([^,]+)") do
    table.insert(t, str)
  end
  return t
end

local job = ARGV[1]
local now = tonumber(ARGV[2])

-- check the job
local backoff, lastRun, concurrent, resources = unpack(redis.call("HMGET", "${RDB_JOBS}:" .. job, "backoff", "lastRun", "concurrent", "resources"))

if resources == false then
  return "job is gone"
end

-- cannot set job if in backoff
if now < tonumber(lastRun) + tonumber(backoff) then
  return "in back-off " .. now .. " < " .. lastRun .. " + " .. backoff .. " = " .. tonumber(lastRun) + tonumber(backoff)
end

-- cannot set job if too much concurrent
if tonumber(concurrent) >= ${MAX_CONCURRENT} then
  return "too concurrent"
end

-- check resources
resources = split(resources, ",")

for i,r in ipairs(resources) do
  local rLastUse, rRateLimit = unpack(redis.call("HMGET", "${RDB_RESOURCES}:" .. r, "lastUseTime", "rateLimit"))

  -- cannot set job if resource not ready
  if now < rLastUse + rRateLimit then
    return "resource '" .. r .. "' is not ready"
  end
end

-- job can be done
for i, r in ipairs(resources) do
  redis.call("HSET", "${RDB_RESOURCES}:" .. r, "lastUseTime", now)

  local curJobs = redis.call("HGET", "${RDB_RESOURCES}:" .. r, "jobQueue")

  -- move the job to the end of the queue
  local newJobs, ops = string.gsub(curJobs, job .. ",", "")
  if ops > 0 then
    redis.call("HSET", "${RDB_RESOURCES}:" .. r, "jobQueue", newJobs .. "," .. job)
  end
end

redis.call("HMSET", "${RDB_JOBS}:" .. job, "lastRun", now * 10, "concurrent", tonumber(concurrent) + 1)
`;

const LUA_ADD_JOB = `
local function split(inputstr)
  local t={}
  for str in string.gmatch(inputstr, "([^,]+)") do
    table.insert(t, str)
  end
  return t
end

local jobName = ARGV[1]

local resources = redis.call("HGET", "${RDB_JOBS}:" .. jobName, "resources")

-- add this job to all applicable resources
resources = split(resources, ",")

for i,r in ipairs(resources) do
  local jobs = redis.call("HGET", "${RDB_RESOURCES}:" .. r, "jobQueue")
  
  jobs = string.gsub(jobs, jobName .. ",|," .. jobName, "")

  if string.len(jobs) == 0 then
    jobs = jobName
  else
    jobs = jobs .. "," .. jobName
  end

  redis.call("HSET", "${RDB_RESOURCES}:" .. r, "jobQueue", jobs)
end
`;

const LUA_CLEAR_JOB = `
local function split(inputstr)
  local t={}
  for str in string.gmatch(inputstr, "([^,]+)") do
    table.insert(t, str)
  end
  return t
end

local jobName = ARGV[1]

local resources = redis.call("HGET", "${RDB_JOBS}:" .. jobName, "resources")

if resources == nil or resources == "" then
  return "job is gone"
end

-- remove this job from all applicable resources
resources = split(resources, ",")

for i,r in ipairs(resources) do
  local jobs = redis.call("HGET", "${RDB_RESOURCES}:" .. r, "jobQueue")
  
  jobs = string.gsub(jobs, jobName .. ",", "")
  jobs = string.gsub(jobs, "," .. jobName, "")
  jobs = string.gsub(jobs, jobName, "")

  redis.call("HSET", "${RDB_RESOURCES}:" .. r, "jobQueue", jobs)
end

redis.call("DEL", "${RDB_JOBS}:" .. jobName)
`;

rdb.defineCommand('schedstart', {
  numberOfKeys: 0,
  lua: LUA_MARK_START_JOB
});

rdb.defineCommand('schedjobadd', {
  numberOfKeys: 0,
  lua: LUA_ADD_JOB
})

rdb.defineCommand('schedjobclear', {
  numberOfKeys: 0,
  lua: LUA_CLEAR_JOB
})

export interface InitResource {
  /** string used to identify this resource */
  name: string;

  /** minimum millisecond interval between calls to this resource */
  rateLimit: number;
}

export interface Resource extends InitResource {
  /** epoch milliseconds of the last time the resource was "used" */
  lastUseTime: number;

  /** list of job names which are next in line to use the resource */
  jobQueue: string;
}

export interface InitJob {
  /** string used to identify this job (and to deduplicate the same job running twice) */
  name: string;

  /** resources which this job is waiting for */
  resources: string[];

  /** function to call to get the next set of input data */
  generator: string;

  /** function which does the actual work of processing the input data */
  task: string;

  /** number of milliseconds before call to `task` is considered timed out */
  timeout: number;
}

export type GenericInitJob = InitJob;

export interface Job extends InitJob {

  /** last cursor value executed */
  cur: CursorData<any>|null;

  /** number of currently processing job segments */
  concurrent: number;

  /** epoch milliseconds of the last time a job segment was started. may be used to temporarily lock a job. */
  lastRun: number;

  /** number of milliseconds to wait before running this job again after failure */
  backoff: number;
}

export type GenericJob = Job;

export interface ScheduledJob {
  /** frequency in milliseconds that this job should be run */
  interval: number;

  /** the job to run */
  job: GenericInitJob;
}

export interface CursorData<T> {
  /** string for the generator to identify the next position in the cursor */
  pos: any|null;

  /** date that the data in this cursor was generated */
  asOf: number;

  /** human readable explaination of the current position of the cursor */
  desc: string;

  /** the items contained in this batch returned by the generator */
  items: T[];
}

export interface SchedConfig {
  /** database which should be used for recording the result of the jobs */
  db: DBConfig;

  /** resources which the jobs will be able to access */
  resources: { [key: string]: InitResource };

  /** references to functions which can be called by the scheduler */
  generators: { [key: string]: (pos: CursorData<any>|null) => Promise<CursorData<any>|null> };

  /** references to function which can be called by the scheduler */
  tasks: { [key: string]: (data: CursorData<any>) => Promise<any> };

  /** jobs which should be put on a schedule */
  jobs: { [key: string]: ScheduledJob };
};

export class Sched {

  /** stored copy of the scheduler configuration */
  readonly config: SchedConfig;
  
  /** the database connection object */
  private db: DB|null = null;

  /** pending call to the `loop` function, in case a cancel is needed */
  private loopPending: any|null = null;

  /** pending calls to push scheduled jobs, in case a cancel is needed */
  private readonly pendingTasks: {[id: string]: any} = {};

  get storedb(): DB {
    return this.db!
  }

  constructor(config: SchedConfig, db: DB | null = null) {
    this.config = config;
    this.db = db;
  }
  
  private async mark_job_exec_start(name: string, now: number): Promise<boolean> {
    return await (<any>rdb).schedstart(name, now);
  }
  
  private async mark_job_exec_new_cursor(job: GenericJob, now: number, cursor: CursorData<any>|null): Promise<void> {
    await rdb.hmset(`${RDB_JOBS}:${job.name}`, {
      lastRun: now,
      cur: JSON.stringify(cursor)
    });
  }
  
  private async mark_job_exec_finish(job: GenericJob): Promise<void> {
    await rdb.hincrby(`${RDB_JOBS}:${job.name}`, 'concurrent', -1);
  }
  
  private async push_failed_segment(job: GenericJob, seg: CursorData<any>): Promise<void> {
    await rdb.rpush(`${RDB_FAILED_SEGMENTS}:${job.name}`, JSON.stringify(seg));
  }
  
  private async pop_failed_segment(job: GenericJob): Promise<CursorData<any>|null> {
    const raw_seg = await rdb.lpop(`${RDB_FAILED_SEGMENTS}:${job.name}`);
  
    if(raw_seg)
      return JSON.parse(raw_seg);
  
    return null;
  }
  
  private async set_resource(r: InitResource): Promise<void> {

    const new_res: Resource = _.defaults(r, {
      jobQueue: '',
      lastUseTime: 0
    });

    await rdb.hmset(`${RDB_RESOURCES}:${r.name}`, new_res);
  }
  
  private async get_resource(name: string): Promise<Resource> {
    const res = await rdb.hgetall(`${RDB_RESOURCES}:${name}`) as any;

    res.lastUseTime = parseInt(res.lastUseTime);
    res.rateLimit = parseInt(res.rateLimit);

    return res;
  }
  
  private async get_all_resources(): Promise<{[id: string]: Resource}> {
    const all_resources = await rdb.scan(0, 'match', `${RDB_RESOURCES}:*`, 'count', 100);

    const rets: Resource[] = [];
    await Promise.all(_.map(all_resources[1], async (r) => {
      rets.push(await this.get_resource(r.split(':')[1]));
    }));

    return _.keyBy(rets, 'name');
  }

  private async set_job(job: GenericJob): Promise<void> {
    // we only want to set some properties on first init (determined by lastRun === 0)
    await rdb.hmset(`${RDB_JOBS}:${job.name}`, _.omit(job, job.lastRun === 0 ? ['cur'] : ['cur', 'concurrent', 'lastRun']));
  }
  
  private async clear_job(job: GenericJob): Promise<void> {
    debug(`clear job: ${job.name}`)
    return await (<any>rdb).schedjobclear(job.name);
  }
  
  async get_job(name: string): Promise<GenericJob|null> {
    const raw_job = await rdb.hgetall(`${RDB_JOBS}:${name}`);
  
    if(raw_job && _.keys(raw_job).length) {

      raw_job.concurrent = parseInt(<string>raw_job.concurrent);
      raw_job.lastRun = parseInt(<string>raw_job.lastRun);
      raw_job.backoff = parseInt(<string>raw_job.backoff);

      if (raw_job.cur) {
        raw_job.cur = JSON.parse(<string>raw_job.cur)
      }

      return raw_job as any;
    }
  
    return null;
  }

  async get_job_failure_count(name: string): Promise<number> {
    return await rdb.llen(`${RDB_FAILED_SEGMENTS}:${name}`);
  }
  
  async push_job(job: GenericInitJob): Promise<void> {
    if(await this.get_job(job.name))
      throw new Error(`job already exists/running: ${job.name}`);
    
    await this.set_job(_.assign({ lastRun: 0, concurrent: 0, backoff: 0, cur: null }, job));
    await (<any>rdb).schedjobadd(job.name);
  }
  
  /** perform a single iteration of executing jobs. may spawn tasks which continue in the background. */
  async loop(repeat = false) {
    debug('enter loop');

    try {
  
      const resources = await this.get_all_resources();
  
      const now = Date.now();
      let nextEvent = 10000;
  
      const doneJobs: GenericJob[] = [];
  
      for(const name in resources) {
        debug(`check resource: ${name}`);
        const r = resources[name];
    
        // can execute?
        if(r.jobQueue && r.jobQueue.length && r.lastUseTime + r.rateLimit <= now) {
          // find the first job that works
          let doJob: GenericJob|null = null;
  
          let jobNextEvent = 10000;
  
          for(const j of r.jobQueue.split(',')) {
            // try to start the job
            const jobStatus = await this.mark_job_exec_start(j, now);
            
            debug(`check job: ${j} (${jobStatus})`);
            if (!jobStatus) {
              // job is qualified
              doJob = await this.get_job(j);
              break;
            }
          }
  
          if(!doJob) {
            // nothing can execute right now
            nextEvent = Math.min(nextEvent, now + jobNextEvent);
          }
          else {
            debugInfo(`do job: ${doJob.name}`);
            doneJobs.push(doJob);
            
            // do this job
            (async () => {
              try {
                // pull the next data from the generator
                const newCur = await (<bluebird<CursorData<any>>>this.config.generators[doJob.generator](doJob.cur)).timeout(doJob.timeout);
  
                await this.mark_job_exec_new_cursor(doJob!, now, newCur);
  
                if (newCur) {
                  try {
                    // process the next data that was generated
                    if(newCur) {
                      await (<bluebird<CursorData<any>>>this.config.tasks[doJob.task](newCur)).timeout(doJob.timeout);

                      debug(`complete job seg: ${doJob.name}`);
                      
                      // after success, backoff is reset
                      doJob!.backoff = 0;
                      await this.set_job(doJob!);
                    }
          
                    // last batch segment?
                    if(!newCur?.pos) {
                      await this.clear_job(doJob);
                    }
                    else {
                      await this.mark_job_exec_finish(doJob);
                    }
                  } catch(err) {
                    debug(`job seg fail (dead): ${doJob.name}: %O`, err);
                    await this.push_failed_segment(doJob, newCur!);
                    await this.mark_job_exec_finish(doJob);
                  }
                }
              } catch(err) {
                debugInfo('failed generating job segment:', doJob!.name, err);
  
                doJob!.backoff = Math.min(Math.max(doJob!.backoff * 2, MIN_BACKOFF), MAX_BACKOFF);
  
                await this.set_job(doJob)

                await this.mark_job_exec_finish(doJob);
  
              }
            })()
          }
        }
        else
          nextEvent = Math.min(nextEvent - DUST_NEXT_EVENT, r.lastUseTime + r.rateLimit - now);
      }
  
      if(repeat)
        this.loopPending = setTimeout(_.bind(this.loop, this, true), nextEvent);
  
    } catch(err) {
      console.error('problem in sched:', err);
  
      if(repeat)
        this.loopPending = setTimeout(_.bind(this.loop, this, true), 1000);
    }
  }
  
  async init() {

    // add resources
    for(const res of _.values(this.config.resources)) {
      debugInfo(`init resource ${res.name}`);
      await this.set_resource(res);
    }
  
    const now = Date.now();
  
    for (const job of _.values(this.config.jobs)) {

      debug('eval %O', job);

      const scheduled: number = await rdb!.hget('jobSchedule', job.job.name) as any;
  
      const runAndSchedule = async () => {
        try {
          await this.push_job(job.job);
          this.pendingTasks[job.job.name] = setTimeout(runAndSchedule, job.interval);
        } catch(err) {
          // schedule again in some time (not the given interval because we want to resume once the existing is done)
          this.pendingTasks[job.job.name] = setTimeout(runAndSchedule, RUNNING_WAIT);
        }

      }
  
      if (scheduled && scheduled > now) {
        // schedule for the time difference
        const scheduleFromNow = now - scheduled;
        debug(`scheduled time difference: ${job.job.name} in ${scheduleFromNow}`);
  
        this.pendingTasks[job.job.name] = setTimeout(runAndSchedule, scheduleFromNow);
  
        continue;
      }
  
      // if we make it this far, spawn right now. schedule for later
      debug(`schedule now: ${job.job.name}`)
      runAndSchedule().then(_.noop)
    }
  }
  
  async exec() {
    await this.init()
  
    this.loop(true).then(_.noop);
  }

  async reprocess_dead(jobName: string) {
    await this.init()

    const job = await this.get_job(jobName)!;

    if(!job)
      throw new Error('job not registered');

    let seg;
    while(seg = await this.pop_failed_segment(job)) {
      try {
        await (<bluebird<CursorData<any>>>this.config.tasks[job.task](seg)).timeout(job.timeout);
      } catch(err) {
        debug(`still fails: %O`, err);
        await this.push_failed_segment(job, seg);
      }
    }
  }

  async close() {
    if(this.db)
      await close_db(this.db);
    
    if(this.loopPending)
      clearTimeout(this.loopPending);

    for(const task in this.pendingTasks) {
      clearTimeout(this.pendingTasks[task]);
    }
  }
}

if(module === require.main) {
  load_db(load_config().db).then((db) => {
    const sched = new Sched(load_config().sched, db);
    sched.exec();
  });
}