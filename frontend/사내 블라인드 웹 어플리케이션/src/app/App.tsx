import { useEffect, useState } from "react";
import type {
  Category,
  Comment,
  FeedView,
  Notification,
  Page,
  Post,
  PostFormValues,
  SortBy,
  UserData,
} from "./types";
import { mockComments, mockNotifications, mockPosts } from "./data/mockData";
import Sidebar from "./components/Sidebar";
import TopBar from "./components/TopBar";
import LoginPage from "./pages/LoginPage";
import OnboardingPage from "./pages/OnboardingPage";
import SettingsPage from "./pages/SettingsPage";
import FeedPage from "./pages/FeedPage";
import PostDetailPage from "./pages/PostDetailPage";
import PostCreatePage from "./pages/PostCreatePage";
import PostEditPage from "./pages/PostEditPage";

const PREVIEW_LENGTH = 100;

function makePreview(content: string): string {
  const oneLine = content.replace(/\s+/g, " ").trim();
  return oneLine.length > PREVIEW_LENGTH ? `${oneLine.slice(0, PREVIEW_LENGTH)}...` : oneLine;
}

export default function App() {
  // ── 네비게이션 / UI 상태 ──
  const [currentPage, setCurrentPage] = useState<Page>("login");
  const [darkMode, setDarkMode] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  // ── 피드 필터 상태 ──
  const [category, setCategory] = useState<Category>("all");
  const [feedView, setFeedView] = useState<FeedView>("all");
  const [sortBy, setSortBy] = useState<SortBy>("latest");
  const [searchQuery, setSearchQuery] = useState("");

  // ── 데이터 상태 (인메모리, 불변 갱신) ──
  const [posts, setPosts] = useState<Post[]>(mockPosts);
  const [commentsByPostId, setCommentsByPostId] =
    useState<Record<number, Comment[]>>(mockComments);
  const [notifications, setNotifications] = useState<Notification[]>(mockNotifications);
  const [selectedPostId, setSelectedPostId] = useState<number | null>(null);

  const [userData, setUserData] = useState<UserData>({
    nickname: "열정적인스타티",
    cohort: "13기",
    campus: "서울",
  });

  useEffect(() => {
    document.documentElement.classList.toggle("dark", darkMode);
  }, [darkMode]);

  const selectedPost = posts.find((p) => p.id === selectedPostId) ?? null;
  const selectedComments =
    selectedPostId != null ? commentsByPostId[selectedPostId] ?? [] : [];

  // ── 인증 / 네비게이션 ──
  const handleLogin = () => setCurrentPage("nickname-setup");

  const handleOnboardingComplete = (data: UserData) => {
    setUserData(data);
    setCurrentPage("main");
  };

  const handleLogout = () => {
    setUserMenuOpen(false);
    setCurrentPage("login");
  };

  const openSettings = () => {
    setUserMenuOpen(false);
    setCurrentPage("settings");
  };

  const backToFeed = () => {
    setSelectedPostId(null);
    setCurrentPage("main");
  };

  // ── 피드 필터 ──
  const handleSelectCategory = (next: Category) => {
    setCategory(next);
    setFeedView("all");
  };

  const handleSelectFeedView = (view: FeedView) => setFeedView(view);

  // ── 게시글 상호작용 (모두 새 배열/객체로 갱신) ──
  const openPost = (id: number) => {
    setPosts((prev) =>
      prev.map((p) => (p.id === id ? { ...p, viewCount: p.viewCount + 1 } : p)),
    );
    setSelectedPostId(id);
    setNotificationsOpen(false);
    setCurrentPage("post-detail");
  };

  const toggleLike = (id: number) =>
    setPosts((prev) =>
      prev.map((p) =>
        p.id === id
          ? { ...p, isLiked: !p.isLiked, likeCount: p.isLiked ? p.likeCount - 1 : p.likeCount + 1 }
          : p,
      ),
    );

  const toggleBookmark = (id: number) =>
    setPosts((prev) =>
      prev.map((p) => (p.id === id ? { ...p, isBookmarked: !p.isBookmarked } : p)),
    );

  const reportPost = () => window.alert("신고가 접수되었습니다. (데모)");

  const createPost = (values: PostFormValues) => {
    const now = new Date().toISOString();
    const nextId = posts.reduce((max, p) => Math.max(max, p.id), 0) + 1;
    const newPost: Post = {
      id: nextId,
      category: values.category,
      title: values.title,
      preview: makePreview(values.content),
      content: values.content,
      author: userData.nickname,
      cohort: userData.cohort,
      campus: userData.campus,
      createdAt: now,
      updatedAt: now,
      viewCount: 0,
      likeCount: 0,
      commentCount: 0,
      isLiked: false,
      isBookmarked: false,
      isMine: true,
    };
    setPosts((prev) => [newPost, ...prev]);
    // 새 글이 최상단에 보이도록 필터 초기화
    setFeedView("all");
    setCategory("all");
    setSearchQuery("");
    setSortBy("latest");
    setCurrentPage("main");
  };

  const editPost = (id: number) => {
    setSelectedPostId(id);
    setCurrentPage("post-edit");
  };

  const updatePost = (id: number, values: PostFormValues) => {
    setPosts((prev) =>
      prev.map((p) =>
        p.id === id
          ? {
              ...p,
              category: values.category,
              title: values.title,
              content: values.content,
              preview: makePreview(values.content),
              updatedAt: new Date().toISOString(),
            }
          : p,
      ),
    );
    setSelectedPostId(id);
    setCurrentPage("post-detail");
  };

  const deletePost = (id: number) => {
    setPosts((prev) => prev.filter((p) => p.id !== id));
    setCommentsByPostId((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
    setSelectedPostId(null);
    setCurrentPage("main");
  };

  // ── 댓글 ──
  const addComment = (postId: number, content: string) => {
    const newComment: Comment = {
      id: Date.now(),
      content,
      createdAt: new Date().toISOString(),
      isMine: true,
    };
    setCommentsByPostId((prev) => ({
      ...prev,
      [postId]: [...(prev[postId] ?? []), newComment],
    }));
    setPosts((prev) =>
      prev.map((p) => (p.id === postId ? { ...p, commentCount: p.commentCount + 1 } : p)),
    );
  };

  const deleteComment = (postId: number, commentId: number) => {
    setCommentsByPostId((prev) => ({
      ...prev,
      [postId]: (prev[postId] ?? []).filter((c) => c.id !== commentId),
    }));
    setPosts((prev) =>
      prev.map((p) =>
        p.id === postId ? { ...p, commentCount: Math.max(0, p.commentCount - 1) } : p,
      ),
    );
  };

  // ── 알림 ──
  const handleNotificationClick = (notification: Notification) => {
    setNotifications((prev) =>
      prev.map((n) => (n.id === notification.id ? { ...n, isRead: true } : n)),
    );
    setNotificationsOpen(false);
    if (notification.postId != null) openPost(notification.postId);
  };

  const markAllNotificationsRead = () =>
    setNotifications((prev) => prev.map((n) => (n.isRead ? n : { ...n, isRead: true })));

  // ── 렌더 ──
  if (currentPage === "login") {
    return <LoginPage onLogin={handleLogin} />;
  }

  if (currentPage === "nickname-setup") {
    return <OnboardingPage onComplete={handleOnboardingComplete} />;
  }

  if (currentPage === "settings") {
    return (
      <SettingsPage
        userData={userData}
        darkMode={darkMode}
        onToggleDarkMode={() => setDarkMode((d) => !d)}
        onBack={() => setCurrentPage("main")}
      />
    );
  }

  if (currentPage === "post-detail" && selectedPost) {
    return (
      <PostDetailPage
        post={selectedPost}
        comments={selectedComments}
        onBack={backToFeed}
        onToggleLike={toggleLike}
        onToggleBookmark={toggleBookmark}
        onReport={reportPost}
        onEdit={editPost}
        onDelete={deletePost}
        onAddComment={addComment}
        onDeleteComment={deleteComment}
      />
    );
  }

  if (currentPage === "post-create") {
    return <PostCreatePage onBack={backToFeed} onCreate={createPost} />;
  }

  if (currentPage === "post-edit" && selectedPost) {
    return (
      <PostEditPage
        post={selectedPost}
        onBack={() => setCurrentPage("post-detail")}
        onUpdate={updatePost}
      />
    );
  }

  // 기본: 메인 피드 (selectedPost가 없어진 상세/수정 진입도 여기로 폴백)
  return (
    <div className="h-screen w-full bg-background text-foreground flex overflow-hidden">
      <Sidebar
        sidebarOpen={sidebarOpen}
        category={category}
        feedView={feedView}
        userData={userData}
        userMenuOpen={userMenuOpen}
        onSelectCategory={handleSelectCategory}
        onSelectFeedView={handleSelectFeedView}
        onToggleUserMenu={() => setUserMenuOpen((o) => !o)}
        onOpenSettings={openSettings}
        onLogout={handleLogout}
      />

      <div className="flex-1 flex flex-col overflow-hidden">
        <TopBar
          sidebarOpen={sidebarOpen}
          searchQuery={searchQuery}
          darkMode={darkMode}
          notifications={notifications}
          notificationsOpen={notificationsOpen}
          onToggleSidebar={() => setSidebarOpen((o) => !o)}
          onSearchChange={setSearchQuery}
          onToggleDarkMode={() => setDarkMode((d) => !d)}
          onNewPost={() => setCurrentPage("post-create")}
          onToggleNotifications={() => setNotificationsOpen((o) => !o)}
          onNotificationClick={handleNotificationClick}
          onMarkAllRead={markAllNotificationsRead}
        />

        <FeedPage
          posts={posts}
          category={category}
          feedView={feedView}
          sortBy={sortBy}
          searchQuery={searchQuery}
          onSortChange={setSortBy}
          onOpenPost={openPost}
          onToggleBookmark={toggleBookmark}
          onReport={reportPost}
        />
      </div>
    </div>
  );
}
