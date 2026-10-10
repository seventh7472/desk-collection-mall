const state = { token: sessionStorage.getItem('mall-token'), user: null, view: 'products', category: '', q: '', cart: { items: [], total: 0 }, products: [], authMode: 'login', checkoutRequestId: null };
const $ = (selector) => document.querySelector(selector);
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const money = (value) => Number(value).toFixed(2);
let productRequest = 0;

async function api(path, options = {}) {
  const headers = { ...options.headers };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  let response;
  try { response = await fetch(path, { ...options, headers, body: options.body === undefined ? undefined : JSON.stringify(options.body) }); }
  catch { throw new Error('无法连接商城服务，请检查网络后重试。'); }
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && state.token) clearSession();
    throw new Error(data?.message || `请求失败（${response.status}），请稍后重试。`);
  }
  return data;
}

function notify(message, error = false) {
  const notice = $('#notice');
  notice.textContent = message;
  notice.classList.toggle('error', error);
  notice.hidden = false;
}

async function run(button, action) {
  if (button.disabled) return;
  button.disabled = true;
  try { await action(); } catch (error) { notify(error.message, true); }
  finally { button.disabled = false; }
}

function updateAccount() {
  $('#account-name').textContent = state.user?.username || '';
  $('#auth-open').hidden = !!state.user;
  $('#logout').hidden = !state.user;
  $('#cart-count').textContent = state.cart.items.reduce((total, item) => total + item.quantity, 0);
}

function clearSession() {
  state.token = null;
  state.user = null;
  state.cart = { items: [], total: 0 };
  state.checkoutRequestId = null;
  sessionStorage.removeItem('mall-token');
  updateAccount();
  if (state.view === 'cart') renderCart();
  if (state.view === 'orders') $('#orders-content').innerHTML = loginRequired('登录后查看你的订单。');
}

function loginRequired(message) {
  return `<div class="empty"><h2>请先登录</h2><p>${escapeHtml(message)}</p><button class="button" data-auth type="button">登录 / 注册</button></div>`;
}

async function loadProducts() {
  const request = ++productRequest;
  $('#product-status').textContent = '正在加载商品…';
  try {
    const products = await api(`/api/products?${new URLSearchParams({ q: state.q, category: state.category })}`);
    if (request !== productRequest) return;
    state.products = products;
    $('#product-status').textContent = state.q ? `“${state.q}” · 找到 ${products.length} 件商品` : `共 ${products.length} 件商品`;
    $('#products').innerHTML = products.length ? products.map((product) => `<article class="product-card"><img class="product-image" src="${escapeHtml(product.image)}" alt="${escapeHtml(product.name)}" loading="lazy"><div class="product-info"><span class="product-category">${escapeHtml(product.category)}</span><h3>${escapeHtml(product.name)}</h3><p class="product-description">${escapeHtml(product.description)}</p><div class="product-meta"><div><span class="price"><small>¥</small>${money(product.price)}</span><span class="stock">库存 ${product.stock} 件</span></div><button class="button add-button" data-add="${product.id}" type="button" ${product.stock <= 0 ? 'disabled' : ''}>${product.stock <= 0 ? '暂时售罄' : '加入购物车'}</button></div></div></article>`).join('') : '<div class="empty"><h2>没有找到相关商品</h2><p>换个关键词，或选择其他分类试试。</p></div>';
  } catch (error) {
    if (request !== productRequest) return;
    $('#products').innerHTML = '';
    $('#product-status').textContent = error.message;
    notify(error.message, true);
  }
}

async function loadCategories() {
  const categories = await api('/api/categories');
  $('#categories').innerHTML = ['', ...categories].map((category) => `<button class="category ${category === state.category ? 'active' : ''}" data-category="${escapeHtml(category)}" aria-pressed="${category === state.category}" type="button">${escapeHtml(category || '全部商品')}</button>`).join('');
}

async function loadCart() {
  state.cart = await api('/api/cart');
  updateAccount();
  if (state.view === 'cart') renderCart();
}

function renderCart() {
  if (!state.user) { $('#cart-content').innerHTML = loginRequired('登录后添加商品，购物车会保存到你的账户。'); return; }
  if (!state.cart.items.length) {
    $('#cart-content').innerHTML = '<div class="empty"><h2>购物车还是空的</h2><p>去挑选适合你桌面的数码好物。</p><button class="button" data-view="products" type="button">去选购商品</button></div>';
    return;
  }
  $('#cart-content').innerHTML = `<div class="cart-table"><div class="cart-row cart-head" aria-hidden="true"><span>商品</span><span>单价</span><span>数量</span><span>小计</span><span>操作</span></div>${state.cart.items.map((item) => `<div class="cart-row"><strong>${escapeHtml(item.name)}</strong><span>¥ ${money(item.price)}</span><label><span class="sr-only">${escapeHtml(item.name)}的数量</span><input class="quantity-input" type="number" min="1" max="99" step="1" value="${item.quantity}" data-quantity="${item.productId}" data-original="${item.quantity}"></label><strong>¥ ${money(item.subtotal)}</strong><button class="text-button danger" data-remove="${item.productId}" type="button">删除</button></div>`).join('')}</div><div class="cart-summary"><span><span class="muted">合计</span><span class="price"><small>¥</small>${money(state.cart.total)}</span></span><button id="checkout" class="button" type="button">创建订单</button></div>`;
}

async function loadOrders() {
  if (!state.user) { $('#orders-content').innerHTML = loginRequired('登录后查看你的订单。'); return; }
  const orders = await api('/api/orders');
  const labels = { PENDING: '待支付', PAID: '已支付', CANCELLED: '已取消' };
  $('#orders-content').innerHTML = orders.length ? orders.map((order) => `<article class="order-card" data-order="${order.id}"><div class="order-header"><div><div class="order-number">订单号 ${escapeHtml(order.orderNo)}</div><div class="order-time">创建于 ${escapeHtml(new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }))}</div></div><span class="status-badge ${escapeHtml(order.status)}">${escapeHtml(labels[order.status] || order.status)}</span></div><div class="order-items">${order.items.map((item) => `<div class="order-item"><div>${escapeHtml(item.name)}<small>¥ ${money(item.price)} × ${item.quantity}</small></div><strong>¥ ${money(item.subtotal)}</strong></div>`).join('')}</div><div class="order-footer"><div class="order-total">订单合计 <span class="price"><small>¥</small>${money(order.total)}</span></div>${order.status === 'PENDING' ? `<div class="order-actions"><button class="button button-outline" data-cancel="${order.id}" type="button">取消订单</button><button class="button" data-pay="${order.id}" type="button">模拟支付</button></div>` : ''}</div></article>`).join('') : '<div class="empty"><h2>还没有订单</h2><p>从购物车创建你的第一笔订单。</p><button class="button" data-view="products" type="button">去选购商品</button></div>';
}

async function showView(view) {
  state.view = view;
  for (const name of ['products', 'cart', 'orders']) $(`#${name}-view`).hidden = name !== view;
  document.querySelectorAll('.nav-button').forEach((button) => {
    const active = button.dataset.view === view;
    button.classList.toggle('active', active);
    if (active) button.setAttribute('aria-current', 'page'); else button.removeAttribute('aria-current');
  });
  if (view === 'cart') { renderCart(); if (state.user) await loadCart(); }
  if (view === 'orders') await loadOrders();
}

function openAuth(mode = 'login') {
  setAuthMode(mode);
  $('#auth-dialog').showModal();
}

function setAuthMode(mode) {
  state.authMode = mode;
  const register = mode === 'register';
  $('#auth-title').textContent = register ? '创建你的账户' : '欢迎回来';
  $('#auth-submit').textContent = register ? '注册账户' : '登录';
  $('#password').autocomplete = register ? 'new-password' : 'current-password';
  $('#auth-error').hidden = true;
  for (const name of ['login', 'register']) {
    $(`#${name}-tab`).classList.toggle('active', name === mode);
    $(`#${name}-tab`).setAttribute('aria-pressed', name === mode);
  }
}

$('#auth-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = $('#auth-submit');
  if (button.disabled) return;
  button.disabled = true;
  $('#auth-error').hidden = true;
  try {
    const body = { username: $('#username').value.trim(), password: $('#password').value };
    if (state.authMode === 'register') {
      await api('/api/auth/register', { method: 'POST', body });
      setAuthMode('login');
      $('#auth-title').textContent = '注册成功，请登录';
      $('#password').value = '';
      $('#password').focus();
    } else {
      const data = await api('/api/auth/login', { method: 'POST', body });
      state.token = data.token;
      state.user = data.user;
      sessionStorage.setItem('mall-token', data.token);
      updateAccount();
      $('#auth-dialog').close();
      $('#password').value = '';
      notify(`登录成功，欢迎 ${state.user.username}。`);
      await loadCart();
      if (state.view === 'orders') await loadOrders();
    }
  } catch (error) { $('#auth-error').textContent = error.message; $('#auth-error').hidden = false; }
  finally { button.disabled = false; }
});

$('#search-form').addEventListener('submit', (event) => { event.preventDefault(); state.q = $('#search-input').value.trim(); loadProducts(); });
$('#auth-open').addEventListener('click', () => openAuth());
$('#login-tab').addEventListener('click', () => setAuthMode('login'));
$('#register-tab').addEventListener('click', () => setAuthMode('register'));
$('#logout').addEventListener('click', () => { clearSession(); notify('已退出登录。'); });
$('#refresh-orders').addEventListener('click', (event) => run(event.currentTarget, loadOrders));
$('#assistant-open').addEventListener('click', () => $('#assistant-dialog').showModal());
document.querySelectorAll('[data-close]').forEach((button) => button.addEventListener('click', () => document.getElementById(button.dataset.close).close()));

document.addEventListener('click', (event) => {
  const button = event.target.closest('button');
  if (!button || button.disabled) return;
  if (button.dataset.view) run(button, () => showView(button.dataset.view));
  if (button.hasAttribute('data-auth')) openAuth();
  if (button.hasAttribute('data-category')) {
    state.category = button.dataset.category;
    document.querySelectorAll('.category').forEach((category) => { const active = category === button; category.classList.toggle('active', active); category.setAttribute('aria-pressed', active); });
    loadProducts();
  }
  if (button.dataset.add) {
    if (!state.user) { openAuth(); return; }
    run(button, async () => {
      await loadCart();
      const productId = Number(button.dataset.add);
      const quantity = (state.cart.items.find((item) => item.productId === productId)?.quantity || 0) + 1;
      await api(`/api/cart/items/${productId}`, { method: 'PUT', body: { quantity } });
      state.checkoutRequestId = null;
      await loadCart();
      notify('商品已加入购物车，可以在购物车调整数量。');
    });
  }
  if (button.dataset.remove) run(button, async () => {
    await api(`/api/cart/items/${button.dataset.remove}`, { method: 'DELETE' });
    state.checkoutRequestId = null;
    await loadCart();
    notify('已从购物车删除商品。');
  });
  if (button.id === 'checkout') run(button, async () => {
    state.checkoutRequestId ||= crypto.randomUUID();
    const order = await api('/api/orders', { method: 'POST', body: { requestId: state.checkoutRequestId } });
    state.checkoutRequestId = null;
    await loadCart();
    await showView('orders');
    notify(`订单 ${order.orderNo} 创建成功，请完成模拟支付。`);
    await loadProducts();
  });
  if (button.dataset.pay) run(button, async () => {
    await api(`/api/orders/${button.dataset.pay}/pay`, { method: 'POST' });
    await loadOrders();
    notify('模拟支付成功，订单已支付。本次操作未发生真实扣款。');
  });
  if (button.dataset.cancel) run(button, async () => {
    await api(`/api/orders/${button.dataset.cancel}/cancel`, { method: 'POST' });
    await loadOrders();
    notify('订单已取消，商品库存已释放。');
    await loadProducts();
  });
  if (button.dataset.question) { $('#chat-input').value = button.dataset.question; $('#chat-form').requestSubmit(); }
  if (button.dataset.chatProduct) {
    state.q = button.dataset.chatProduct;
    state.category = '';
    $('#search-input').value = state.q;
    $('#assistant-dialog').close();
    showView('products').catch((error) => notify(error.message, true));
    document.querySelectorAll('.category').forEach((category) => { const active = category.dataset.category === ''; category.classList.toggle('active', active); category.setAttribute('aria-pressed', active); });
    loadProducts();
  }
});

document.addEventListener('change', async (event) => {
  const input = event.target.closest('[data-quantity]');
  if (!input) return;
  const quantity = Number(input.value);
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 99) { input.value = input.dataset.original; notify('数量必须是 1–99 的整数。', true); return; }
  input.disabled = true;
  try {
    await api(`/api/cart/items/${input.dataset.quantity}`, { method: 'PUT', body: { quantity } });
    state.checkoutRequestId = null;
    await loadCart();
    notify('购物车数量已更新。');
  } catch (error) { input.value = input.dataset.original; notify(error.message, true); }
  finally { input.disabled = false; }
});

function appendChat(speaker, message, products = []) {
  const entry = document.createElement('div');
  entry.className = `chat-message ${speaker === '你' ? 'user' : 'assistant'}`;
  entry.innerHTML = `<span class="speaker">${escapeHtml(speaker)}</span><p>${escapeHtml(message)}</p>${products.map((product) => `<button class="chat-product" data-chat-product="${escapeHtml(product.name)}" type="button">${escapeHtml(product.name)} · ¥ ${money(product.price)} · 查看商品</button>`).join('')}`;
  $('#chat-messages').append(entry);
  $('#chat-messages').scrollTop = $('#chat-messages').scrollHeight;
}

$('#chat-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const message = $('#chat-input').value.trim();
  const button = $('#chat-form button');
  if (!message || button.disabled) return;
  button.disabled = true;
  appendChat('你', message);
  $('#chat-input').value = '';
  try { const result = await api('/api/assistant/chat', { method: 'POST', body: { message } }); appendChat('购物助手', result.reply, result.products || []); }
  catch (error) { appendChat('购物助手', error.message); }
  finally { button.disabled = false; $('#chat-input').focus(); }
});

async function init() {
  const results = await Promise.allSettled([loadProducts(), loadCategories()]);
  results.forEach((result) => { if (result.status === 'rejected') notify(result.reason.message, true); });
  if (state.token) {
    try { state.user = await api('/api/auth/me'); updateAccount(); await loadCart(); }
    catch (error) { clearSession(); notify(error.message, true); }
  }
}
init();
