const normalizeSearch = value => String(value ?? '')
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .replace(/[đĐ]/g, 'd')
  .toLocaleLowerCase('vi');

export function filterAccounts(accounts, query = '', status = 'all') {
  const needle = normalizeSearch(query.trim());
  return accounts.filter(account => {
    const enabled = account.enabled === true || account.enabled === 'true';
    const matchesStatus = status === 'all' || (status === 'active' ? enabled : !enabled);
    return matchesStatus && normalizeSearch(account.searchText).includes(needle);
  });
}
