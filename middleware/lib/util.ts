

export function generate_unique_id(length: number) {

    // base58 character set. From bitcoin.
    var CHARSET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

    var code = '';
    for(var i = 0;i < length;i++) {
        code += CHARSET.charAt(_.random(CHARSET.length - 1));
    }
    return code;
}