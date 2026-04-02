import { execFileSync } from "node:child_process";

const timezone = process.env.RELEASE_TIMEZONE || "Asia/Jakarta";
const now = new Date();
const parts = new Intl.DateTimeFormat("en-CA", {
  timeZone: timezone,
  year: "numeric",
  month: "numeric",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
}).formatToParts(now);

const values = Object.fromEntries(parts.filter(({ type }) => type !== "literal").map(({ type, value }) => [type, value]));
const baseTag = `v${values.year}.${Number(values.month)}.${Number(values.day)}.${values.hour}${values.minute}`;

const tags = execFileSync("git", ["tag", "--list", `${baseTag}*`], {
  encoding: "utf8",
}).split("\n").map((tag) => tag.trim()).filter(Boolean);

if (!tags.includes(baseTag)) {
  process.stdout.write(baseTag);
  process.exit(0);
}

let suffix = 1;
while (tags.includes(`${baseTag}.${suffix}`)) {
  suffix += 1;
}

process.stdout.write(`${baseTag}.${suffix}`);
