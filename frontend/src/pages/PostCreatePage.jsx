import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import api from '../api/client';
import PostForm from '../components/PostForm';

// 새 글 작성 — PostForm + POST /posts {category, title, content}
export default function PostCreatePage() {
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleCreate = async (values) => {
    setError('');
    setSubmitting(true);
    try {
      const res = await api.post('/posts', values);
      navigate(`/posts/${res.data.id}`);
    } catch (err) {
      setError(err.response?.data?.message || '게시글 작성에 실패했습니다.');
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen w-full bg-background text-foreground overflow-y-auto">
      <div className="max-w-3xl mx-auto p-6">
        <button
          onClick={() => navigate('/feed')}
          className="flex items-center gap-2 text-sm font-mono text-muted-foreground hover:text-foreground transition-colors mb-4"
        >
          <ArrowLeft size={16} />
          피드로 돌아가기
        </button>

        <h1 className="text-2xl font-mono mb-6">새 글 작성</h1>

        <PostForm
          initialValues={{ category: 'FREE', title: '', content: '' }}
          submitLabel="작성 완료"
          submitting={submitting}
          error={error}
          onSubmit={handleCreate}
          onCancel={() => navigate('/feed')}
          allowPoll /* [FEATURE:poll] 투표는 작성 시에만 첨부(수정 페이지에는 미전달) */
        />
      </div>
    </div>
  );
}
