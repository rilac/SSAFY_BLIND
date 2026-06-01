-- [FEATURE:qna-accept] Q&A 채택/해결됨 — QUESTION 글의 채택된 답변(댓글) id.
-- null이면 미해결. FK 없이 bigint(채택 댓글 삭제 시 서비스가 정리) — Post.acceptedCommentId 와 일치.
alter table posts add column accepted_comment_id bigint;
