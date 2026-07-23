import crypto from "node:crypto";

// NetEase weapi fixed keys
const presetKey = Buffer.from("0CoJUm6Qyw8W8jud", "utf8");
const iv = Buffer.from("0102030405060708", "utf8");
const publicKey =
  "010001";
const modulus =
  "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";

const base62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

function rsaEncrypt(text) {
  const reversedText = [...text].reverse().join("");
  const biText = BigInt("0x" + Buffer.from(reversedText, "utf8").toString("hex"));
  const biEx = BigInt("0x" + publicKey);
  const biMod = BigInt("0x" + modulus);
  const encrypted = biText ** biEx % biMod;
  return encrypted.toString(16).padStart(256, "0");
}

function createSecretKey(size) {
  let key = "";
  for (let i = 0; i < size; i++) {
    key += base62[Math.floor(Math.random() * base62.length)];
  }
  return key;
}

function aesEncrypt(plaintext, key) {
  const cipher = crypto.createCipheriv("aes-128-cbc", key, iv);
  const encrypted = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
  return encrypted.toString("base64");
}

/**
 * Encrypt a plain payload object using NetEase weapi scheme.
 * @param {object} payload
 * @returns {{params: string, encSecKey: string}}
 */
export function weapi(payload) {
  const text = JSON.stringify(payload);
  const secretKey = createSecretKey(16);
  return {
    params: aesEncrypt(aesEncrypt(text, presetKey), Buffer.from(secretKey, "utf8")),
    encSecKey: rsaEncrypt(secretKey),
  };
}
