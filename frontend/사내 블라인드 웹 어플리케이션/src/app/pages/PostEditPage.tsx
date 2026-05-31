import { ArrowLeft } from "lucide-react";
import type { Post, PostFormValues } from "../types";
import PostForm from "../components/PostForm";

interface PostEditPageProps {
  post: Post;
  onBack: () => void;
  onUpdate: (id: number, values: PostFormValues) => void;
}

// 게시글 수정 — 기존 값 prefill 후 PostForm 재사용
export default function PostEditPage({ post, onBack, onUpdate }: PostEditPageProps) {
  return (
    <div className="h-screen w-full bg-background text-foreground overflow-y-auto">
      <div className="max-w-3xl mx-auto p-6">
        <button
          onClick={onBack}
          className="flex items-center gap-2 text-sm font-mono text-muted-foreground hover:text-foreground transition-colors mb-4"
        >
          <ArrowLeft size={16} />
          돌아가기
        </button>

        <h1 className="text-2xl font-mono mb-6">게시글 수정</h1>

        <PostForm
          initialValues={{
            category: post.category,
            title: post.title,
            content: post.content,
          }}
          submitLabel="수정 완료"
          onSubmit={(values) => onUpdate(post.id, values)}
          onCancel={onBack}
        />
      </div>
    </div>
  );
}
