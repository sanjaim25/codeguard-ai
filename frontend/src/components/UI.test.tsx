import {afterEach,describe,expect,it,vi} from 'vitest';
import {act,cleanup,fireEvent,render,screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {Loading,ErrorState} from './Common';
import {DiffViewer} from './DiffViewer';
import {parseDiff} from './diff';
import {ToastProvider,useToast} from './Toast';
import {Repositories} from '../pages/Repositories';
import {api,ApiError} from '../api/client';
import type {ChangedFile,Finding,Page,Repository} from '../api/types';

vi.mock('../api/client',async importOriginal=>({...await importOriginal<typeof import('../api/client')>(),api:vi.fn()}));
vi.mock('./Layout',()=>({useWorkspace:()=>({demo:true,profile:{login:'demo'}})}));
afterEach(()=>{cleanup();vi.clearAllMocks();});
const fixture:Page<Repository>={content:[{id:'repo-1',fullName:'demo/service',privateRepository:true,language:'Java',reviewEnabled:true,cloudAiOptIn:false,publishingEnabled:false,autoPublish:false}],page:0,size:12,totalElements:1,totalPages:1};
function repositories() {const client=new QueryClient({defaultOptions:{queries:{retry:false,gcTime:0}}});return render(<QueryClientProvider client={client}><MemoryRouter><Repositories/></MemoryRouter></QueryClientProvider>);}
describe('workspace states',()=>{
  it('announces loading and offers an actionable retry',()=>{const retry=vi.fn();const view=render(<Loading label="Loading reviews"/>);expect(screen.getByRole('status')).toHaveAccessibleName('Loading reviews');view.rerender(<ErrorState error={new Error('Connection unavailable')} retry={retry}/>);expect(screen.getByRole('alert')).toHaveTextContent('Connection unavailable');fireEvent.click(screen.getByRole('button',{name:'Try again'}));expect(retry).toHaveBeenCalledOnce();});
  it('loads real API rows and sends search criteria',async()=>{let resolve!:(value:Page<Repository>)=>void;vi.mocked(api).mockImplementation(path=>path==='/api/auth/config'?Promise.resolve({githubEnabled:false,installationUrl:''}):new Promise(done=>{resolve=done as typeof resolve;}));repositories();expect(screen.getByRole('status')).toBeInTheDocument();await act(async()=>resolve(fixture));expect(await screen.findByRole('link',{name:'demo/service'})).toHaveAttribute('href','/app/repositories/repo-1');vi.mocked(api).mockResolvedValue({...fixture,content:[],totalElements:0,totalPages:0});fireEvent.change(screen.getByRole('textbox',{name:'Search repositories'}),{target:{value:'missing'}});expect(await screen.findByText('No matching repositories')).toBeInTheDocument();expect(api).toHaveBeenCalledWith(expect.stringContaining('search=missing'));});
  it('shows API failure without inventing repository rows',async()=>{vi.mocked(api).mockRejectedValue(new ApiError(503,'Service unavailable'));repositories();expect(await screen.findByRole('alert')).toHaveTextContent('Service unavailable');expect(screen.queryByText('demo/service')).not.toBeInTheDocument();});
});
describe('safe diff review',()=>{
  it('tracks both sides across hunks and distinguishes removed lines',()=>{const lines=parseDiff('@@ -3,2 +3,3 @@\n context\n-old\n+new\n+extra\n@@ -20 +21 @@\n-old\n+last\n');expect(lines.filter(line=>line.kind==='added').map(line=>line.newLine)).toEqual([4,5,21]);expect(lines.filter(line=>line.kind==='removed').map(line=>line.oldLine)).toEqual([4,20]);});
  it('renders untrusted text safely and links a finding to an added line',()=>{const files:ChangedFile[]=[{path:'src/A.java',status:'modified',patch:'@@ -1 +1 @@\n-old\n+<img src=x onerror=alert(1)>\n',addedLines:[1],truncated:false}];const findings:Finding[]=[{id:'finding',data:{title:'Review input',description:'<script>alert(1)</script>',filePath:'src/A.java',line:1,severity:'HIGH',category:'SECURITY',suggestedFix:'Validate input',confidence:.8,explanation:'Untrusted input reaches an interpreter.'}}];const view=render(<DiffViewer files={files} findings={findings}/>);expect(view.container.querySelector('img')).toBeNull();expect(view.container.querySelector('script')).toBeNull();expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'1 finding at line 1'}));expect(view.container.querySelector('.focused-line')).toHaveTextContent('img');expect(screen.getByText('Validate input')).toBeVisible();fireEvent.change(screen.getByRole('combobox',{name:'Filter findings by severity'}),{target:{value:'LOW'}});expect(screen.getByText('No matching findings')).toBeInTheDocument();});
});
describe('toast notifications',()=>{
  it('renders and auto-dismisses toast',()=>{
    vi.useFakeTimers();
    function TestComponent() {
      const toast = useToast();
      return <button onClick={()=>toast.success('Review saved')}>Trigger</button>;
    }
    render(<ToastProvider><TestComponent/></ToastProvider>);
    fireEvent.click(screen.getByRole('button',{name:'Trigger'}));
    expect(screen.getByRole('alert')).toHaveTextContent('Review saved');
    act(()=>{vi.advanceTimersByTime(4100);});
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    vi.useRealTimers();
  });
});
