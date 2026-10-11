'use strict';
const DAY = /^\d{4}-\d{2}-\d{2}$/;
function validDate(value) {
  if (typeof value !== 'string' || !DAY.test(value)) return false;
  const parsed = new Date(value + 'T00:00:00Z');
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}
function today() {
  const parts = new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Colombo',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
  const get = type => parts.find(p => p.type === type).value;
  return `${get('year')}-${get('month')}-${get('day')}`;
}
function department(role) {
  role = String(role || '').toLowerCase();
  return role.includes('sales') ? 'Sales' : role.includes('hotel') ? 'Hotel' : role.includes('office') || role.includes('staff') ? 'Office' : 'Other';
}
function balances(attendance, leaves, currentDate = today()) {
  const dates = new Set(attendance.filter(a => validDate(a.date) && a.date <= currentDate && a.checkIn && a.checkOut && a.workStatus === 'Completed').map(a => a.date));
  const worked = dates.size, earned = Math.floor(worked / 10);
  const used = leaves.filter(l => l.status === 'approved').length;
  const pending = leaves.filter(l => l.status === 'pending').length;
  return {worked, earned, used, pending, remaining: Math.max(0,earned-used-pending), nextWorkDays: Math.max(0,(used+pending+1)*10-worked)};
}
function blockedReason(date, config, holiday, currentDate = today()) {
  if (!validDate(date)) return 'Select a valid leave date.';
  if (date < currentDate) return 'Past dates cannot be booked.';
  if (!config || !validDate(config.holidayCoverageStart) || !validDate(config.holidayCoverageEnd) || date < config.holidayCoverageStart || date > config.holidayCoverageEnd) return 'Company holiday calendar is not confirmed for this date.';
  const weekday = new Date(date+'T00:00:00Z').getUTCDay();
  if ((config.blockedWeekdays || []).includes(weekday)) return 'Leave cannot be taken on a company holiday.';
  if (holiday && holiday.blocked !== false) return 'Leave cannot be taken on a holiday: '+(holiday.name || date);
  return '';
}
function employeeBlockedReason(role,date,config,holiday,departmentDay,currentDate=today()) {
  if(!validDate(date))return 'Select a valid leave date.';
  if(date<currentDate)return 'Past dates cannot be booked.';
  // Company holiday/coverage restriction is a Sales-only leave policy.
  if(department(role)!=='Sales')return '';
  return blockedReason(date,config,holiday,currentDate) || (departmentDay?.kind==='holiday'?'Leave cannot be taken on a company holiday.':'');
}
function allowance(employee, leaves, date = today()) {
  const year = Number(date.slice(0,4));
  const total = employee.annualLeaveAllowance;
  const opening = employee.annualLeaveUsedBeforeApp;
  const configured = ['Office','Hotel'].includes(department(employee.role))
    && employee.annualLeaveYear === year && Number.isInteger(total) && total >= 0 && total <= 366
    && Number.isInteger(opening) && opening >= 0 && opening <= total;
  const relevant = leaves.filter(l => validDate(l.date) && Number(l.date.slice(0,4)) === year);
  const approved = relevant.filter(l => l.status === 'approved').length;
  const pending = relevant.filter(l => l.status === 'pending').length;
  const used = (configured ? opening : 0) + approved;
  return {configured,year,quota:configured?total:'#',earned:configured?total:0,
    used,pending,remaining:configured?Math.max(0,total-used-pending):0,worked:0,nextWorkDays:0};
}
function employeeBalance(employee, attendance, leaves, date = today()) {
  return department(employee.role) === 'Sales' ? {configured:true,...balances(attendance,leaves)}
    : allowance(employee,leaves,date);
}
function normalizePhone(value){
  let phone=String(value || '').replace(/[\s()-]/g,'');
  if(/^0\d{9}$/.test(phone))phone='+94'+phone.slice(1);
  else if(/^94\d{9}$/.test(phone))phone='+'+phone;
  else if(/^\d{9}$/.test(phone))phone='+94'+phone;
  return /^\+94\d{9}$/.test(phone)?phone:'';
}
module.exports = {validDate,today,department,balances,blockedReason,allowance,employeeBalance,normalizePhone,employeeBlockedReason};
