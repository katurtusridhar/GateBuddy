import { SubjectInfo } from "./types";

export const SUBJECT_REGISTRY: SubjectInfo[] = [
  { code: "DL", name: "Digital Logic", priorCompletedLectures: 41, totalLectures: 52, type: "RECORDED" },
  { code: "CN", name: "Computer Network", priorCompletedLectures: 18, totalLectures: 73, type: "RECORDED" },
  { code: "DBMS", name: "DBMS", priorCompletedLectures: 27, totalLectures: 42, type: "RECORDED" },
  { code: "C-PROG", name: "Fundamental of C + C Prog", priorCompletedLectures: 0, totalLectures: 43, type: "RECORDED" },
  { code: "BOC", name: "Basics Of Computer System", priorCompletedLectures: 0, totalLectures: 10, type: "RECORDED" },
  { code: "DS", name: "Data Structures (Old PW)", priorCompletedLectures: 0, totalLectures: 29, type: "RECORDED" },
  { code: "COA", name: "COA", priorCompletedLectures: 0, totalLectures: 51, type: "LIVE" },
  { code: "EM", name: "Foundation of Engg Maths", priorCompletedLectures: 0, totalLectures: 25, type: "RECORDED" },
  { code: "LA", name: "Linear Algebra", priorCompletedLectures: 0, totalLectures: 23, type: "LIVE" },
  { code: "OS", name: "Operating System", priorCompletedLectures: 0, totalLectures: 43, type: "LIVE" },
  { code: "PS", name: "Probability & Statistics", priorCompletedLectures: 0, totalLectures: 41, type: "LIVE" },
  { code: "ALGO", name: "Algorithms", priorCompletedLectures: 0, totalLectures: 52, type: "RECORDED" },
  { code: "CALC", name: "Calculus & Optimization", priorCompletedLectures: 0, totalLectures: 23, type: "LIVE" },
  { code: "GA", name: "General Aptitude", priorCompletedLectures: 0, totalLectures: 29, type: "LIVE" },
  { code: "DM", name: "Discrete Mathematics", priorCompletedLectures: 0, totalLectures: 53, type: "LIVE" },
  { code: "TOC", name: "TOC", priorCompletedLectures: 0, totalLectures: 29, type: "RECORDED" },
  { code: "CD", name: "Compiler Design", priorCompletedLectures: 0, totalLectures: 22, type: "RECORDED" },
  { code: "VA", name: "Verbal Aptitude", priorCompletedLectures: 0, totalLectures: 5, type: "LIVE" },
  { code: "MOCK", name: "Full-Syllabus Revision & Mock Practice", priorCompletedLectures: 0, totalLectures: 15, type: "REVISION_MOCK" },
  { code: "REV", name: "Saturday Revision", priorCompletedLectures: 0, totalLectures: 6, type: "REVISION" },
  { code: "M-BUF", name: "Math Buffer & Revision", priorCompletedLectures: 0, totalLectures: 3, type: "BUFFER" },
  { code: "BUF", name: "Buffer & Catch-Up", priorCompletedLectures: 0, totalLectures: 12, type: "BUFFER" },
];

const nameToCodeMap = new Map<string, string>();
const codeToInfoMap = new Map<string, SubjectInfo>();

for (const s of SUBJECT_REGISTRY) {
  nameToCodeMap.set(s.name.toLowerCase().trim(), s.code);
  codeToInfoMap.set(s.code.toUpperCase(), s);
}

export function getCodeForSubject(subjectName: string): string {
  const clean = subjectName.trim().toLowerCase();
  const direct = nameToCodeMap.get(clean);
  if (direct) return direct;

  if (clean.includes("digital")) return "DL";
  if (clean.includes("network")) return "CN";
  if (clean.includes("dbms")) return "DBMS";
  if (clean.includes("c prog") || clean.includes("fundamental of c")) return "C-PROG";
  if (clean.includes("basics of computer")) return "BOC";
  if (clean.includes("data structure")) return "DS";
  if (clean.includes("coa")) return "COA";
  if (clean.includes("math buffer")) return "M-BUF";
  if (clean.includes("engg math") || clean.includes("foundation of engg")) return "EM";
  if (clean.includes("linear algebra")) return "LA";
  if (clean.includes("operating system")) return "OS";
  if (clean.includes("probability")) return "PS";
  if (clean.includes("algorithm")) return "ALGO";
  if (clean.includes("calculus")) return "CALC";
  if (clean.includes("general aptitude")) return "GA";
  if (clean.includes("discrete")) return "DM";
  if (clean.includes("toc") || clean.includes("theory of comp")) return "TOC";
  if (clean.includes("compiler")) return "CD";
  if (clean.includes("verbal")) return "VA";
  if (clean.includes("mock") || clean.includes("full-syllabus")) return "MOCK";
  if (clean.includes("revision")) return "REV";
  if (clean.includes("buffer")) return "BUF";

  return clean.slice(0, 4).toUpperCase();
}

export function getSubjectInfoByCode(code: string): SubjectInfo | undefined {
  return codeToInfoMap.get(code.trim().toUpperCase());
}

export function getPriorCompletedLectures(subjectName: string): number {
  const code = getCodeForSubject(subjectName);
  return codeToInfoMap.get(code)?.priorCompletedLectures ?? 0;
}
