export const mockTraining = {
  sessionId: "preview-1",
  status: "IN_PROGRESS",
  photo: {
    id: 12,
    originalFilename: "family_trip.jpg",
    confirmedTakenOn: "2025-05-10",
    confirmedLocation: "부산 해운대",
  },
  questions: [
    {
      questionId: 31,
      questionOrder: 1,
      type: "PHOTO_CLUE",
      content: "이 사진은 어디에서 촬영되었나요?",
    },
    {
      questionId: 32,
      questionOrder: 2,
      type: "EXPERIENCE",
      content: "이 사진을 찍었을 때 누구와 함께 있었나요?",
    },
    {
      questionId: 33,
      questionOrder: 3,
      type: "CONTEXT",
      content: "이 순간에 대해 기억나는 이야기를 자유롭게 들려주세요.",
    },
  ],
};

export const TRAINING_PREVIEW_KEY = "memory-recall-training-preview";

export function exampleSubmission() {
  return {
    sessionId: mockTraining.sessionId,
    completedAt: new Date().toISOString(),
    answers: {
      31: {
        status: "ANSWERED",
        content: "부산 해운대에서 촬영한 사진입니다.",
      },
      32: {
        status: "ANSWERED",
        content: "가족과 함께 여행을 갔습니다.",
      },
      33: {
        status: "CANNOT_RECALL",
        content: "",
      },
    },
  };
}
