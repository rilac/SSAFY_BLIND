import { ArrowLeft } from "lucide-react";
import type { PostFormValues } from "../types";
import { CATEGORY_LABELS } from "../data/mockData";
import PostForm from "../components/PostForm";

interface PostCreatePageProps {
  onBack: () => void;
  onCreate: (values: PostFormValues) => void;
}

// 새 글 작성
export default function PostCreatePage({ onBack, onCreate }: PostCreatePageProps) {
  return (
    <div className="h-screen w-full bg-background text-foreground overflow-y-auto">
      <div className="max-w-3xl mx-auto p-6">
        <button
          onClick={onBack}
          className="flex items-center gap-2 text-sm font-mono text-muted-foreground hover:text-foreground transition-colors mb-4"
        >
          <ArrowLeft size={16} />
          피드로 돌아가기
        </button>

        <h1 className="text-2xl font-mono mb-6">새 글 작성</h1>

        <PostForm
          initialValues={{ category: CATEGORY_LABELS.free, title: "", content: "" }}
          submitLabel="작성 완료"
          onSubmit={onCreate}
          onCancel={onBack}
        />
      </div>
    </div>
  );
}
