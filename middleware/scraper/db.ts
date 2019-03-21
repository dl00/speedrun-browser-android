

export const ID_LENGTH = 12;

export function join(parts: string[]) {
    return parts.join(':');
}

export function split(str: string) {
    return str.split(':');
}

/// key values/prefixes for which datasets can be found
export const locs = {
    pending_tasks: 'pending_tasks',
    running_tasks: 'running_tasks',

    task_calls: 'task_calls',
    callqueue: 'queue'
}