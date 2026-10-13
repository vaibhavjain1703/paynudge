const rupees = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 0 });
const rupeesWithPaise = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2 });

/** 15000000 paise reads "₹1,50,000"; 150050 reads "₹1,500.50". Display only: the API does all money math in paise. */
export function formatPaise(paise: number) {
  return (paise % 100 === 0 ? rupees : rupeesWithPaise).format(paise / 100);
}

const dateFormat = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });

/** "2026-11-05" reads "5 Nov 2026", whatever the viewer's timezone. */
export function formatDate(isoDate: string) {
  return dateFormat.format(new Date(`${isoDate}T00:00:00Z`));
}

/** "2026-11-03T04:30:00Z" in Asia/Kolkata reads "3 Nov 2026, 10:00": reminder times are shown in business time. */
export function formatDateTime(isoInstant: string, timeZone: string) {
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone,
  }).format(new Date(isoInstant));
}
