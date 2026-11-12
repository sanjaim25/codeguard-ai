import {useEffect,useState} from 'react';
import {NavLink, Navigate, Outlet, Link, useLocation, useOutletContext} from 'react-router-dom';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {BarChart3, BookOpen, Boxes, ChevronRight, GitPullRequest, LayoutDashboard, LogOut, Settings, ShieldCheck, RefreshCw, Moon, Sun} from 'lucide-react';
import {api, ApiError} from '../api/client';
import type {AuthConfig, Profile, System} from '../api/types';
import {ErrorState, Loading} from './Common';

type Workspace = {profile:Profile; demo:boolean};
export const useWorkspace = () => useOutletContext<Workspace>();
type Theme = 'dark'|'light';
const themeStorageKey = 'codeguard-theme';
function initialTheme(): Theme {
  try { return window.localStorage.getItem(themeStorageKey) === 'light' ? 'light' : 'dark'; }
  catch { return 'dark'; }
}
const navigation = [{to:'/app',label:'Overview',icon:LayoutDashboard,end:true},{to:'/app/repositories',label:'Repositories',icon:Boxes},{to:'/app/pull-requests',label:'Pull requests',icon:GitPullRequest},{to:'/app/analytics',label:'Analytics',icon:BarChart3},{to:'/app/settings',label:'Settings',icon:Settings}];
export function Brand() { return <span className="brand"><span className="brand-icon"><ShieldCheck size={23}/></span>CodeGuard<span className="brand-ai">AI</span></span>; }
export function Layout() {
  const client = useQueryClient(); const location = useLocation();
  const [theme,setTheme] = useState<Theme>(initialTheme);
  const profile = useQuery({queryKey:['me'],queryFn:()=>api<Profile>('/api/me'),retry:false});
  const system = useQuery({queryKey:['system'],queryFn:()=>api<System>('/api/system')});
  const auth = useQuery({queryKey:['auth-config'],queryFn:()=>api<AuthConfig>('/api/auth/config')});
  const sync = useQuery({queryKey:['github-sync'],queryFn:()=>api('/api/github/sync',{method:'POST'}),enabled:!!profile.data && auth.data?.githubEnabled === true && system.data?.demo === false,refetchInterval:5*60*1000,staleTime:4*60*1000,retry:false});
  useEffect(() => { if (sync.dataUpdatedAt) void client.invalidateQueries({predicate:query=>['repositories','pull-requests','analytics'].includes(String(query.queryKey[0]))}); }, [sync.dataUpdatedAt, client]);
  const logout = useMutation({mutationFn:()=>api('/api/logout',{method:'POST'}),onSuccess:()=>{client.clear(); window.location.assign('/signin');}});
  useEffect(()=>{document.documentElement.dataset.theme=theme; document.documentElement.style.colorScheme=theme; try { window.localStorage.setItem(themeStorageKey,theme); } catch { /* Appearance preference is optional. */ }},[theme]);
  if (profile.error instanceof ApiError && profile.error.status === 401) return <Navigate to="/signin" replace state={{from:location.pathname}}/>;
  if (profile.isPending || system.isPending) return <div className="full-loading"><Loading/></div>;
  if (profile.error || system.error) return <ErrorState error={profile.error ?? system.error} retry={()=>{void profile.refetch(); void system.refetch();}}/>;
  const current = navigation.find(item => item.end ? location.pathname === item.to : location.pathname.startsWith(item.to));
  return <div className="app-shell"><a className="skip-link" href="#main-content">Skip to content</a>
    <aside className="sidebar"><Link to="/app" aria-label="CodeGuard overview"><Brand/></Link><div className="workspace-label"><span className="workspace-avatar">{system.data.demo ? 'D' : profile.data.login[0].toUpperCase()}</span><div><strong>{system.data.demo ? 'Demo workspace' : profile.data.login}</strong><small>{system.data.demo ? 'Local sample environment' : 'Developer workspace'}</small></div></div>
      <div className="nav-label">WORKSPACE</div><nav aria-label="Main navigation">{navigation.map(({to,label,icon:Icon,end})=><NavLink key={to} to={to} end={end} className={({isActive})=>isActive?'nav-link active':'nav-link'}><Icon size={18}/>{label}</NavLink>)}</nav>
      <div className="sidebar-bottom"><div className="advisory-card"><ShieldCheck size={20}/><strong>A second pair of eyes.</strong><p>AI findings support your review. Your judgment makes the final call.</p></div><a href="/api/docs" target="_blank" rel="noreferrer" className="nav-link"><BookOpen size={18}/>API documentation</a><button className="nav-link" onClick={()=>logout.mutate()} disabled={logout.isPending}><LogOut size={18}/>Sign out</button>{logout.error && <p role="alert" className="text-error">Sign out failed. Try again.</p>}</div>
    </aside>
    <div className="workspace-main"><header className="topbar"><div className="breadcrumb">Workspace<ChevronRight size={14}/><span>{current?.label ?? 'Review detail'}</span></div><div className="topbar-right"><span className={`environment ${system.data.demo?'demo':''}`}><span className="status-dot"/>{system.data.demo?'DEMO MODE':'CONNECTED WORKSPACE'}</span><button className="icon-button theme-toggle" aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`} title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`} onClick={()=>setTheme(value=>value === 'dark' ? 'light' : 'dark')}>{theme === 'dark' ? <Sun size={16}/> : <Moon size={16}/>}</button><button className="icon-button mobile-signout" aria-label="Sign out of workspace" onClick={()=>logout.mutate()} disabled={logout.isPending}><LogOut size={16}/></button><span className="profile-avatar" title={profile.data.login}>{profile.data.login.slice(0,2).toUpperCase()}</span></div></header>
      {system.data.demo && <div className="demo-banner">Sample repositories and stored demo reviews. Cloud AI and GitHub publishing are disabled.</div>}
      {sync.error && <div className="notice warning" role="alert">Repository access could not be refreshed. <button className="text-button" onClick={()=>void sync.refetch()}><RefreshCw size={14}/>Retry sync</button> <a href="/oauth2/authorization/github">Sign in again</a></div>}
      <main id="main-content" className="main-content"><Outlet context={{profile:profile.data,demo:system.data.demo} satisfies Workspace}/></main>
      <footer className="app-footer">CodeGuard AI <span>Advisory reviews. Human decisions.</span></footer>
    </div></div>;
}
