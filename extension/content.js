// tells the Reelm site the helper is installed (value = version) and relays its fetch bridge to the extension worker
document.documentElement.dataset.reelmHelper = chrome.runtime.getManifest().version;
addEventListener("message", (e) => {
  if (e.source !== window || e.origin !== location.origin || e.data?.reelm !== "req") return; // location.origin is one of the manifest matches
  const id = e.data.id, reply = (r) => postMessage({ reelm: "res", id, ...r }, location.origin);
  chrome.runtime.sendMessage(e.data.msg, (r) => reply(chrome.runtime.lastError ? { error: chrome.runtime.lastError.message } : r || { error: "no reply" }));
});
