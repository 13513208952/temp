/* Offline reader interaction: bounded PLAIN-TEXT rendering only.
 * No innerHTML/outerHTML/document.write/eval/dynamic script, no network, no file access and no
 * native/connector message channel. Data must come from the native owner through render().
 */
(function () {
  'use strict';
  var MAX_SUBJECT = 512;
  var MAX_META = 256;
  var MAX_PARAGRAPHS = 2000;
  var MAX_PARAGRAPH_CHARS = 20000;
  var MAX_ATTACHMENTS = 100;
  var MAX_ATTACHMENT_NAME = 256;

  function element(id) {
    var node = document.getElementById(id);
    if (!node) throw new Error('reader structure missing: ' + id);
    return node;
  }

  function boundedText(value, max) {
    if (typeof value !== 'string') throw new Error('text required');
    if (value.length > max) throw new Error('text too long');
    return value;
  }

  function clear(node) {
    while (node.firstChild) node.removeChild(node.firstChild);
  }

  function setStatus(state, message) {
    var status = element('status');
    status.setAttribute('data-state', state);
    status.textContent = boundedText(message, MAX_META);
  }

  function paragraph(text) {
    var p = document.createElement('p');
    p.textContent = text;
    return p;
  }

  function renderAttachments(items) {
    var list = element('attachments');
    clear(list);
    if (!Array.isArray(items) || items.length === 0) { list.hidden = true; return; }
    if (items.length > MAX_ATTACHMENTS) throw new Error('too many attachments');
    var heading = document.createElement('h2');
    heading.textContent = '附件（仅显示名称）';
    var ul = document.createElement('ul');
    for (var i = 0; i < items.length; i++) {
      var name = boundedText(items[i], MAX_ATTACHMENT_NAME);
      var li = document.createElement('li');
      li.textContent = name;
      ul.appendChild(li);
    }
    list.appendChild(heading);
    list.appendChild(ul);
    list.hidden = false;
  }

  function render(payload) {
    if (!payload || typeof payload !== 'object') throw new Error('payload required');
    var subject = boundedText(payload.subject || '离线邮件', MAX_SUBJECT);
    var meta = boundedText(payload.meta || '', MAX_META);
    element('subject').textContent = subject;
    element('meta').textContent = meta;
    var body = element('body');
    clear(body);
    var paragraphs = payload.paragraphs;
    if (!Array.isArray(paragraphs) || paragraphs.length === 0) {
      setStatus('empty', '没有可显示的正文');
      renderAttachments(payload.attachments || []);
      return;
    }
    if (paragraphs.length > MAX_PARAGRAPHS) throw new Error('too many paragraphs');
    for (var i = 0; i < paragraphs.length; i++) {
      var text = boundedText(paragraphs[i], MAX_PARAGRAPH_CHARS);
      body.appendChild(paragraph(text));
    }
    setStatus('ready', '纯文本正文 · 不加载远程内容');
    renderAttachments(payload.attachments || []);
  }

  function showUnavailable(reason) {
    element('subject').textContent = '离线邮件';
    element('meta').textContent = '';
    clear(element('body'));
    renderAttachments([]);
    setStatus('unknown', boundedText(reason || '离线内容不可用', MAX_META));
  }

  function showFailure(reason) {
    clear(element('body'));
    renderAttachments([]);
    setStatus('error', boundedText(reason || '读取失败', MAX_META));
  }

  /** Bounded literal search highlight over rendered text nodes only (no HTML parsing). */
  function highlight(literal) {
    var needle = boundedText(literal, 64);
    var body = element('body');
    var walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT, null, false);
    var nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      var index = node.nodeValue.indexOf(needle);
      if (index < 0) continue;
      var range = document.createRange();
      range.setStart(node, index);
      range.setEnd(node, index + needle.length);
      var mark = document.createElement('mark');
      mark.className = 'search-hit';
      try { range.surroundContents(mark); } catch (splitBoundary) { /* skipped, never retried via HTML */ }
    }
  }

  window.ReBrowserReader = Object.freeze({
    render: render,
    showUnavailable: showUnavailable,
    showFailure: showFailure,
    highlight: highlight
  });

  // Default: nothing loaded. The native owner must explicitly supply bounded plain text.
  showUnavailable('等待原生离线邮件内容');
})();
