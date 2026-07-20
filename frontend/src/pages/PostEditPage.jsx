import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext'; // 관리자 여부 판별
import PostForm from '../components/PostForm';

// 게시글 수정 — 기존 데이터 로드 후 PostForm prefill → PUT /posts/{id}
export default function PostEditPage() {
  const navigate = useNavigate();
  const { id } = useParams();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [initialValues, setInitialValues] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchPost = async () => {
      try {
        const res = await api.get(`/posts/${id}`);
        const { category, title, content, isMine, hidden } = res.data;
        // 본인 글도 관리자도 아니면 수정 불가 → 상세로 리다이렉트(관리자는 카테고리 교정 등 허용)
        if (!isMine && !isAdmin) {
          navigate(`/posts/${id}`, { replace: true });
          return;
        }
        // [FEATURE:hidden-author-visibility] 숨김 글은 서버가 수정을 400으로 막는다(검수 회피 방지).
        // 작성자는 이제 자기 숨김 글을 볼 수 있어 /edit URL로 직접 들어올 수 있으므로, 폼을 띄우고
        // 저장 시점에 실패시키는 대신 여기서 상세로 되돌린다(상세에 사유 배너가 있다).
        if (hidden) {
          navigate(`/posts/${id}`, { replace: true });
          return;
        }
        setInitialValues({ category: category || 'FREE', title, content });
      } catch (err) {
        setError('게시글을 불러올 수 없습니다.');
      } finally {
        setLoading(false);
      }
    };
    fetchPost();
  }, [id, navigate, isAdmin]);

  const handleUpdate = async (values) => {
    setError('');
    setSubmitting(true);
    try {
      await api.put(`/posts/${id}`, values);
      navigate(`/posts/${id}`);
    } catch (err) {
      setError(err.response?.data?.message || '게시글 수정에 실패했습니다.');
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen w-full bg-background text-foreground overflow-y-auto">
      <div className="max-w-3xl mx-auto p-4 sm:p-6">
        <button
          onClick={() => navigate(`/posts/${id}`)}
          className="flex items-center gap-2 text-sm font-mono text-muted-foreground hover:text-foreground transition-colors mb-4"
        >
          <ArrowLeft size={16} />
          돌아가기
        </button>

        <h1 className="text-2xl font-mono mb-6">게시글 수정</h1>

        {loading ? (
          <p className="text-sm font-mono text-muted-foreground">불러오는 중...</p>
        ) : initialValues ? (
          <PostForm
            initialValues={initialValues}
            submitLabel="수정 완료"
            submitting={submitting}
            error={error}
            onSubmit={handleUpdate}
            onCancel={() => navigate(`/posts/${id}`)}
          />
        ) : (
          <p className="text-sm font-mono text-destructive">{error || '게시글을 찾을 수 없습니다.'}</p>
        )}
      </div>
    </div>
  );
}
