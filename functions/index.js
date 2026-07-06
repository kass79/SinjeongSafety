const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * posts 컬렉션에 새 문서가 생기면 "new_posts" 토픽 구독자 전원에게 푸시 발송
 * 리전: 서울 (asia-northeast3)
 */
exports.notifyNewPost = onDocumentCreated(
  { document: "posts/{postId}", region: "asia-northeast3" },
  async (event) => {
    const post = event.data?.data();
    if (!post) return;

    const title = `📢 ${post.category || "새 안전정보"}`;
    const body = post.title || "새 게시물이 등록되었습니다";

    await getMessaging().send({
      topic: "new_posts",
      notification: { title, body },
      data: { postId: event.params.postId, title, body },
      android: {
        priority: "high",
        notification: { channelId: "new_posts", icon: "ic_notification" },
      },
    });
    console.log(`푸시 발송 완료: ${body}`);
  }
);
