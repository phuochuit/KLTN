const navigation = document.querySelector('#navigationDialog');
const openNavigation = document.querySelector('#openNavigation');
const closeNavigation = document.querySelector('#closeNavigation');
const secondaryFilters = document.querySelector('.sessions-secondary-filters');
const compactFilters = matchMedia('(max-width: 47.999rem)');

function syncSecondaryFilters() {
  if (secondaryFilters) secondaryFilters.open = !compactFilters.matches;
}
syncSecondaryFilters();
compactFilters.addEventListener('change', syncSecondaryFilters);

openNavigation?.addEventListener('click', () => {
  navigation?.showModal();
  closeNavigation?.focus();
});
closeNavigation?.addEventListener('click', () => navigation?.close());
matchMedia('(min-width: 64rem)').addEventListener('change', event => {
  if (event.matches && navigation?.open) navigation.close();
});

document.querySelectorAll('.dashboard-logout').forEach(form => form.addEventListener('submit', event => {
  if (!window.confirm('Bạn muốn đăng xuất khỏi hệ thống?')) event.preventDefault();
}));
