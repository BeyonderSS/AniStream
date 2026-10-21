// Ed25519 signing for scrapers.json so clients can reject a tampered file.
//   node sign.mjs keygen            prints SCRAPER_SIGNING_KEY (repo secret) and the public key (bake into the clients)
//   node sign.mjs sign <file>       needs env SCRAPER_SIGNING_KEY, writes <file>.sig (base64)
//   node sign.mjs verify <file> <publicKeyBase64>
import { createPrivateKey, createPublicKey, generateKeyPairSync, sign, verify } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";

const [, , cmd, file, pub] = process.argv;
if (cmd === "keygen") {
  const { privateKey, publicKey } = generateKeyPairSync("ed25519");
  console.log("SCRAPER_SIGNING_KEY=" + privateKey.export({ type: "pkcs8", format: "der" }).toString("base64"));
  console.log("PUBLIC_KEY=" + publicKey.export({ type: "spki", format: "der" }).toString("base64"));
} else if (cmd === "sign") {
  const key = createPrivateKey({ key: Buffer.from(process.env.SCRAPER_SIGNING_KEY, "base64"), format: "der", type: "pkcs8" });
  writeFileSync(file + ".sig", sign(null, readFileSync(file), key).toString("base64") + "\n");
} else if (cmd === "verify") {
  const key = createPublicKey({ key: Buffer.from(pub, "base64"), format: "der", type: "spki" });
  const ok = verify(null, readFileSync(file), key, Buffer.from(readFileSync(file + ".sig", "utf8").trim(), "base64"));
  console.log(ok ? "ok" : "BAD SIGNATURE");
  process.exit(ok ? 0 : 1);
} else { console.error("usage: sign.mjs keygen | sign <file> | verify <file> <pub>"); process.exit(2); }
