import { validateReschedule } from "../recoveryEngine";
import { ScheduleTask, RecoveryAssignment } from "../types";

// Mock tasks
const mockTasks: ScheduleTask[] = [
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

describe("Recovery Engine Business Rules", () => {
  it("enforces Rule 1: Max 2 recoveries per day", () => {
    const targetDate = "2026-10-25";
    const existingRecoveries: RecoveryAssignment[] = [
      {
        id: 1,
        taskId: "DL_41",
        originalDate: "2026-10-19",
        recoveryDate: targetDate,
        assignedAtTimestamp: Date.now(),
        isCompleted: false,
        priorityLevel: 2,
        status: "ACTIVE",
      },
      {
        id: 2,
        taskId: "CN_18",
        originalDate: "2026-10-19",
        recoveryDate: targetDate,
        assignedAtTimestamp: Date.now(),
        isCompleted: false,
        priorityLevel: 2,
        status: "ACTIVE",
      },
    ];

    const validation = validateReschedule(
      "DL_42",
      targetDate,
      "2026-10-21",
      existingRecoveries,
      mockTasks
    );

    expect(validation.valid).toBe(false);
    expect(validation.error).toContain("Rule 1 Violation");
  });

  it("enforces Rule 2: No 2 lectures of the same subject on the same day", () => {
    const targetDate = "2026-10-25";
    const existingRecoveries: RecoveryAssignment[] = [
      {
        id: 1,
        taskId: "DL_42",
        originalDate: "2026-10-21",
        recoveryDate: targetDate,
        assignedAtTimestamp: Date.now(),
        isCompleted: false,
        priorityLevel: 2,
        status: "ACTIVE",
      },
    ];

    // Attempt to schedule another DL task on same day
    const validation = validateReschedule(
      "DL_42",
      targetDate,
      "2026-10-21",
      existingRecoveries,
      mockTasks
    );

    // If it's already assigned to itself, it's valid to keep
    expect(validation.valid).toBe(true);

    // But if a different task of same subject attempts to join
    const mockDLTask2: ScheduleTask = {
      ...mockTasks[0],
      id: "DL_43",
      curNum: 43,
    };

    const validation2 = validateReschedule(
      "DL_43",
      targetDate,
      "2026-10-21",
      existingRecoveries,
      [...mockTasks, mockDLTask2]
    );

    expect(validation2.valid).toBe(false);
    expect(validation2.error).toContain("Rule 2 Violation");
  });
});
