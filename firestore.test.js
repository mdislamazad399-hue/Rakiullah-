const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const TEACHER_UID = "teacher_alice_123";
const STUDENT_UID = "student_bob_456";
const OTHER_UID = "other_charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("decks").where("published", "==", true).get());
  await assertFails(unauth.collection("users").doc(TEACHER_UID).get());
  await assertFails(
    unauth.collection("decks").doc("deck_1").set({
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      title: "Cell Biology",
      subject: "Biology",
      description: "Core cell organelles",
      joinCode: "BIO101",
      accentColorKey: "SAGE",
      published: true,
      flashcardCount: 0,
      quizCount: 0,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("User profile: owner can create and update; cross-user read/write and ghost fields fail", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    teacherDb.collection("users").doc(TEACHER_UID).set({
      userId: TEACHER_UID,
      displayName: "Prof. Alice",
      role: "TEACHER",
      totalXp: 0,
      quizzesCompleted: 0,
      cardsReviewed: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update of allowed fields succeeds
  await assertSucceeds(
    teacherDb.collection("users").doc(TEACHER_UID).update({
      role: "STUDENT",
      totalXp: 50,
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user read & write rejected
  await assertFails(studentDb.collection("users").doc(TEACHER_UID).get());
  await assertFails(studentDb.collection("users").doc(TEACHER_UID).update({ totalXp: 999 }));

  // Ghost field & future timestamp rejected
  await assertFails(
    studentDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      displayName: "Bob",
      role: "STUDENT",
      totalXp: 0,
      quizzesCompleted: 0,
      cardsReviewed: 0,
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    studentDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      displayName: "Bob",
      role: "STUDENT",
      totalXp: 0,
      quizzesCompleted: 0,
      cardsReviewed: 0,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );
});

test("StudyDecks, Flashcards, QuizQuestions: teacher ownership & published visibility", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const created = pastDate(10000);

  // Teacher creates a published deck (no `id` field written)
  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").set({
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      title: "Cell Biology 101",
      subject: "Biology",
      description: "Mitochondria and cellular respiration",
      joinCode: "BIO101",
      accentColorKey: "SAGE",
      published: true,
      flashcardCount: 0,
      quizCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Teacher creates a draft (unpublished) deck
  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_draft").set({
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      title: "Secret Draft",
      subject: "Biology",
      description: "",
      joinCode: "DRF999",
      accentColorKey: "INDIGO",
      published: false,
      flashcardCount: 0,
      quizCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Teacher adds flashcard & quiz question
  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").collection("flashcards").doc("card_1").set({
      deckId: "deck_bio",
      teacherId: TEACHER_UID,
      front: "What is the powerhouse of the cell?",
      back: "Mitochondrion",
      hint: "Produces ATP",
      orderIndex: 0,
      published: true,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").collection("questions").doc("q_1").set({
      deckId: "deck_bio",
      teacherId: TEACHER_UID,
      prompt: "Which molecule carries cellular energy?",
      options: ["ATP", "DNA", "Glucose", "RNA"],
      correctIndex: 0,
      explanation: "ATP stores energy in its phosphate bonds.",
      orderIndex: 0,
      published: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Teacher queries own decks; student queries published decks by joinCode
  await assertSucceeds(teacherDb.collection("decks").where("teacherId", "==", TEACHER_UID).get());
  await assertSucceeds(
    studentDb.collection("decks").where("published", "==", true).where("joinCode", "==", "BIO101").get()
  );
  await assertSucceeds(
    studentDb.collection("decks").doc("deck_bio").collection("flashcards").where("published", "==", true).get()
  );
  await assertSucceeds(
    studentDb.collection("decks").doc("deck_bio").collection("questions").where("published", "==", true).get()
  );

  // Unfiltered decks query fails; draft deck read by student fails
  await assertFails(studentDb.collection("decks").get());
  await assertFails(studentDb.collection("decks").doc("deck_draft").get());

  // Student cannot edit or delete teacher's deck or flashcards
  await assertFails(studentDb.collection("decks").doc("deck_bio").update({ title: "Hacked" }));
  await assertFails(
    studentDb.collection("decks").doc("deck_bio").collection("flashcards").doc("card_1").delete()
  );

  // Extra `id` field rejected on create
  await assertFails(
    teacherDb.collection("decks").doc("deck_bad").set({
      id: "deck_bad",
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      title: "Bad",
      subject: "Math",
      description: "",
      joinCode: "MATH01",
      accentColorKey: "INDIGO",
      published: true,
      flashcardCount: 0,
      quizCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("DeckProgress, Enrollments & StudyLogs: student writes own progress; teacher reads class progress", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").set({
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      title: "Cell Biology 101",
      subject: "Biology",
      description: "Organelles",
      joinCode: "BIO101",
      accentColorKey: "SAGE",
      published: true,
      flashcardCount: 1,
      quizCount: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Student writes progress to deck_bio/progress/{STUDENT_UID}
  await assertSucceeds(
    studentDb.collection("decks").doc("deck_bio").collection("progress").doc(STUDENT_UID).set({
      deckId: "deck_bio",
      teacherId: TEACHER_UID,
      studentId: STUDENT_UID,
      studentName: "Bob Student",
      masteredCardIds: ["card_1"],
      reviewedCardsCount: 1,
      totalCardsCount: 1,
      quizzesTaken: 1,
      bestQuizPercent: 100,
      lastQuizPercent: 100,
      lastQuizCorrect: 1,
      lastQuizTotal: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Student updates own progress
  await assertSucceeds(
    studentDb.collection("decks").doc("deck_bio").collection("progress").doc(STUDENT_UID).update({
      reviewedCardsCount: 2,
      updatedAt: pastDate(1000),
    })
  );

  // Teacher reads all student progress for their deck using teacherId filter
  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").collection("progress").where("teacherId", "==", TEACHER_UID).get()
  );

  // Another student cannot read or tamper with Bob's progress
  await assertFails(
    otherDb.collection("decks").doc("deck_bio").collection("progress").doc(STUDENT_UID).get()
  );
  await assertFails(
    otherDb.collection("decks").doc("deck_bio").collection("progress").doc(STUDENT_UID).update({
      bestQuizPercent: 0,
      updatedAt: pastDate(500),
    })
  );

  // Student writes own enrollment & study log
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("enrollments").doc("deck_bio").set({
      deckId: "deck_bio",
      deckTitle: "Cell Biology 101",
      subject: "Biology",
      teacherName: "Prof. Alice",
      accentColorKey: "SAGE",
      masteredCount: 1,
      totalCards: 1,
      bestQuizPercent: 100,
      quizzesTaken: 1,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("studyLogs").doc("log_1").set({
      deckId: "deck_bio",
      deckTitle: "Cell Biology 101",
      mode: "QUIZ",
      scorePercent: 100,
      correctCount: 1,
      totalCount: 1,
      xpEarned: 35,
      createdAt: created,
    })
  );
  await assertSucceeds(studentDb.collection("users").doc(STUDENT_UID).collection("enrollments").get());
  await assertSucceeds(studentDb.collection("users").doc(STUDENT_UID).collection("studyLogs").get());

  // Cross-user enrollment/studyLogs access denied
  await assertFails(otherDb.collection("users").doc(STUDENT_UID).collection("enrollments").get());
  await assertFails(otherDb.collection("users").doc(STUDENT_UID).collection("studyLogs").get());
});

test("LiveSessions: teacher can broadcast and update live class; students can read published live sessions only", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").set({
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      title: "Cell Biology 101",
      subject: "Biology",
      description: "Organelles",
      joinCode: "BIO101",
      accentColorKey: "SAGE",
      published: true,
      flashcardCount: 1,
      quizCount: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Teacher creates live session
  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").collection("liveSessions").doc("active").set({
      deckId: "deck_bio",
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      active: true,
      published: true,
      activeItemType: "FLASHCARD",
      activeItemIndex: 0,
      activePrompt: "What is the powerhouse of the cell?",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Teacher updates live session item
  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").collection("liveSessions").doc("active").update({
      activeItemType: "QUIZ",
      activeItemIndex: 0,
      activePrompt: "Which molecule carries cellular energy?",
      updatedAt: pastDate(1000),
    })
  );

  // Student can read published live session, cannot overwrite it
  await assertSucceeds(
    studentDb.collection("decks").doc("deck_bio").collection("liveSessions").doc("active").get()
  );
  await assertFails(
    studentDb.collection("decks").doc("deck_bio").collection("liveSessions").doc("active").update({
      active: false,
      updatedAt: pastDate(500),
    })
  );
});

test("StudyVideos: teacher can upload, update, and delete; students read published videos only", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").set({
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      title: "Cell Biology 101",
      subject: "Biology",
      description: "Organelles",
      joinCode: "BIO101",
      accentColorKey: "SAGE",
      published: true,
      flashcardCount: 0,
      quizCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").collection("videos").doc("vid_1").set({
      deckId: "deck_bio",
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      title: "Mitosis Walkthrough",
      notes: "Watch before quiz",
      fileName: "study_vid_1.mp4",
      durationMs: 92000,
      fileSizeBytes: 10485760,
      published: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    studentDb.collection("decks").doc("deck_bio").collection("videos").where("published", "==", true).get()
  );

  await assertFails(
    studentDb.collection("decks").doc("deck_bio").collection("videos").doc("vid_1").delete()
  );

  await assertSucceeds(
    teacherDb.collection("decks").doc("deck_bio").collection("videos").doc("vid_1").delete()
  );
});

test("StudyBatches, StudyWallet, Transactions, BatchEnrollments & BatchMembers: rules enforce ownership, search queries, and payment enrollment", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const created = pastDate(10000);

  // Teacher creates a paid batch with cover image base64
  await assertSucceeds(
    teacherDb.collection("batches").doc("batch_neet").set({
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      title: "NEET Physics Master Batch",
      subject: "Physics",
      description: "Full syllabus with live classes and flashcards",
      scheduleText: "Mon-Fri • 6:00 PM",
      batchCode: "NEET26",
      priceInr: 499,
      upiId: "alice@upi",
      coverImageData: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
      accentColorKey: "INDIGO",
      published: true,
      enrolledCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Teacher updates batch metadata; student cannot tamper with batch price
  await assertSucceeds(
    teacherDb.collection("batches").doc("batch_neet").update({
      priceInr: 599,
      updatedAt: pastDate(2000),
    })
  );
  await assertFails(
    studentDb.collection("batches").doc("batch_neet").update({
      priceInr: 0,
      updatedAt: pastDate(1500),
    })
  );

  // Student tops up wallet and records transaction
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("wallet").doc("summary").set({
      userId: STUDENT_UID,
      balanceInr: 1000,
      totalAddedInr: 1000,
      totalSpentInr: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("transactions").doc("tx_topup").set({
      userId: STUDENT_UID,
      type: "TOP_UP",
      amountInr: 1000,
      title: "Added money to Study Wallet",
      batchId: "",
      paymentMethod: "UPI",
      status: "COMPLETED",
      createdAt: created,
    })
  );

  // Cross-user cannot read or update student's wallet or transactions
  await assertFails(otherDb.collection("users").doc(STUDENT_UID).collection("wallet").doc("summary").get());
  await assertFails(otherDb.collection("users").doc(STUDENT_UID).collection("transactions").get());

  // Student pays from wallet & enrolls in published batch
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("wallet").doc("summary").update({
      balanceInr: 401,
      totalSpentInr: 599,
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("transactions").doc("tx_pay").set({
      userId: STUDENT_UID,
      type: "BATCH_PAYMENT",
      amountInr: 599,
      title: "Enrolled in NEET Physics Master Batch",
      batchId: "batch_neet",
      paymentMethod: "WALLET",
      status: "COMPLETED",
      createdAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("batchEnrollments").doc("batch_neet").set({
      batchId: "batch_neet",
      batchTitle: "NEET Physics Master Batch",
      subject: "Physics",
      teacherId: TEACHER_UID,
      teacherName: "Prof. Alice",
      scheduleText: "Mon-Fri • 6:00 PM",
      accentColorKey: "INDIGO",
      coverImageData: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
      amountPaidInr: 599,
      paymentMethod: "WALLET",
      transactionId: "tx_pay",
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    studentDb.collection("batches").doc("batch_neet").collection("members").doc(STUDENT_UID).set({
      batchId: "batch_neet",
      teacherId: TEACHER_UID,
      studentId: STUDENT_UID,
      studentName: "Bob Student",
      amountPaidInr: 599,
      paymentMethod: "WALLET",
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    studentDb.collection("batches").doc("batch_neet").update({
      enrolledCount: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Filtered batch queries succeed; unfiltered list query fails
  await assertSucceeds(studentDb.collection("batches").where("published", "==", true).get());
  await assertSucceeds(teacherDb.collection("batches").where("teacherId", "==", TEACHER_UID).get());
  await assertSucceeds(
    teacherDb.collection("batches").doc("batch_neet").collection("members").where("teacherId", "==", TEACHER_UID).get()
  );
  await assertFails(studentDb.collection("batches").get());
  await assertFails(otherDb.collection("batches").doc("batch_neet").collection("members").doc(STUDENT_UID).get());
});

test("Realistic UID owner get on wallet/summary and list on transactions & batchEnrollments succeed (empty & populated)", async () => {
  const realUid = "EULJobPB7oQZr99nm75o7JDj1GC2";
  const ownerDb = testEnv.authenticatedContext(realUid).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();

  // Empty collection / non-existent doc reads by owner must succeed
  await assertSucceeds(ownerDb.collection("users").doc(realUid).collection("wallet").doc("summary").get());
  await assertSucceeds(ownerDb.collection("users").doc(realUid).collection("transactions").get());
  await assertSucceeds(ownerDb.collection("users").doc(realUid).collection("batchEnrollments").get());

  // Cross-user attempts must fail
  await assertFails(otherDb.collection("users").doc(realUid).collection("wallet").doc("summary").get());
  await assertFails(otherDb.collection("users").doc(realUid).collection("transactions").get());
  await assertFails(otherDb.collection("users").doc(realUid).collection("batchEnrollments").get());
});


