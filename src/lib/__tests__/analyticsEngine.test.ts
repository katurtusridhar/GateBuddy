import { describe, it, expect } from "vitest";
import {
  calculateDailyScore,
  calculateAdherenceAndForecast,
} from "../analyticsEngine";
import { SUBJECT_REGISTRY, getCodeForSubject } from "../subjectRegistry";
import { ScheduleTask, TaskProgress } from "../types";

describe("Analytics Engine & Subject Registry", () => {
  const sampleTasks: ScheduleTask[] = [
    {
      id: "DL_42",
      date: "2026-10-21",
      dayOfWeek: "Wed",
      slot: 1,
      subject: "Digital Logic",
      type: "RECORDED",
      curNum: 42,
      totalCount: 52,
      title: "Digital Logic — Lec 42/52",
      details: "",
      milestone: "",
      sourcePage: 1,
      isBuffer: false,
      isRevision: false,
      isSundayActivity: false,
    },
    {
      id: "CN_19",
      date: "2026-10-21",
      dayOfWeek: "Wed",
      slot: 2,
      subject: "Computer Network",
      type: "RECORDED",
      curNum: 19,
      totalCount: 73,
      title: "Computer Network — Lec 19/73",
      details: "",
      milestone: "",
      sourcePage: 1,
      isBuffer: false,
      isRevision: false,
      isSundayActivity: false,
    },
  ];

  it("calculates 100% daily score when all today tasks are completed", () => {
    const progressMap = new Map<string, TaskProgress>();
    progressMap.set("DL_42", {
      taskId: "DL_42",
      isCompleted: true,
      completedAt: 1729500000000,
      completionType: "ON_TIME",
    });
    progressMap.set("CN_19", {
      taskId: "CN_19",
      isCompleted: true,
      completedAt: 1729505000000,
      completionType: "ON_TIME",
    });

    const score = calculateDailyScore("2026-10-21", "2026-10-21", sampleTasks, progressMap);
    expect(score.scorePercentage).toBe(100);
    expect(score.dailyPoints).toBe(20);
    expect(score.completedScheduledTasks).toBe(2);
  });

  it("applies penalty for uncompleted tasks on past dates", () => {
    const progressMap = new Map<string, TaskProgress>();
    // Target date is in the past compared to current date "2026-10-23"
    const score = calculateDailyScore("2026-10-21", "2026-10-23", sampleTasks, progressMap);
    expect(score.scorePercentage).toBe(0);
    // Uncompleted past tasks receive penalty
    expect(score.statusDescription).toContain("Missed");
  });

  it("correctly identifies subject code and prior completion offsets", () => {
    expect(getCodeForSubject("Digital Logic")).toBe("DL");
    expect(getCodeForSubject("Computer Network")).toBe("CN");
    expect(getCodeForSubject("DBMS")).toBe("DBMS");

    const dl = SUBJECT_REGISTRY.find((s) => s.code === "DL");
    expect(dl).toBeDefined();
    expect(dl?.priorCompletedLectures).toBe(41);
    expect(dl?.totalLectures).toBe(52);

    const cn = SUBJECT_REGISTRY.find((s) => s.code === "CN");
    expect(cn).toBeDefined();
    expect(cn?.priorCompletedLectures).toBe(18);
    expect(cn?.totalLectures).toBe(73);
  });

  it("calculates adherence and health forecast correctly", () => {
    const progressList: TaskProgress[] = [
      {
        taskId: "DL_42",
        isCompleted: true,
        completedAt: 1729500000000,
        completionType: "ON_TIME",
      },
      {
        taskId: "CN_19",
        isCompleted: true,
        completedAt: 1729505000000,
        completionType: "ON_TIME",
      },
    ];

    const forecast = calculateAdherenceAndForecast(
      "2026-10-21",
      sampleTasks,
      progressList
    );

    expect(forecast.onTimeAdherencePercent).toBe(100);
    expect(forecast.health).toBe("ON_TRACK");
    expect(forecast.currentBacklogCount).toBe(0);
    expect(forecast.isAheadOrOnTrack).toBe(true);
  });
});
