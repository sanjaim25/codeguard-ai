import {BrowserRouter,Link,Route,Routes} from 'react-router-dom';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {lazy,Suspense} from 'react';
import {ApiError} from './api/client';
import {Layout} from './components/Layout';
import {EmptyState,Loading} from './components/Common';
import {Landing,SignIn} from './pages/Auth';
const Dashboard=lazy(()=>import('./pages/Dashboard').then(module=>({default:module.Dashboard})));
import {Repositories,RepositoryPage} from './pages/Repositories';
const PullRequests=lazy(()=>import('./pages/PullRequests').then(module=>({default:module.PullRequests})));
const PullRequestPage=lazy(()=>import('./pages/PullRequests').then(module=>({default:module.PullRequestPage})));
const ReviewPage=lazy(()=>import('./pages/PullRequests').then(module=>({default:module.ReviewPage})));
import {Settings} from './pages/Settings';

const client=new QueryClient({defaultOptions:{queries:{staleTime:15000,retry:(attempt,error)=>!(error instanceof ApiError&&error.status<500)&&attempt<1}}});
export function App() {return <QueryClientProvider client={client}><BrowserRouter><Suspense fallback={<div className="full-loading"><Loading/></div>}><Routes><Route path="/" element={<Landing/>}/><Route path="/signin" element={<SignIn/>}/><Route path="/app" element={<Layout/>}><Route index element={<Dashboard/>}/><Route path="repositories" element={<Repositories/>}/><Route path="repositories/:id" element={<RepositoryPage/>}/><Route path="pull-requests" element={<PullRequests/>}/><Route path="pull-requests/:id" element={<PullRequestPage/>}/><Route path="reviews/:id" element={<ReviewPage/>}/><Route path="analytics" element={<Dashboard analyticsOnly/>}/><Route path="settings" element={<Settings/>}/></Route><Route path="*" element={<div className="full-loading"><EmptyState title="This page took a different branch" description="The link may be outdated or the page may have moved." action={<Link className="button primary" to="/app">Back to workspace</Link>}/></div>}/></Routes></Suspense></BrowserRouter></QueryClientProvider>;}
