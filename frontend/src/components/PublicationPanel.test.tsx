import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {api} from '../api/client';
import {PublicationPanel} from './PublicationPanel';
vi.mock('../api/client',()=>({api:vi.fn()}));
afterEach(()=>{cleanup();vi.clearAllMocks();});
const preview={body:'<script>untrusted</script>',comments:[{path:'A.java',line:4,body:'Review this line'}]};
const ready={id:null,status:'NOT_REQUESTED',failureCode:null,githubUrl:null,canPublish:true,preview};
function mount(){return render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false,gcTime:0}}})}><PublicationPanel jobId="job-1"/></QueryClientProvider>);}
it('requires opening the safe preview before submitting and shows durable pending state',async()=>{
  vi.mocked(api).mockResolvedValueOnce(ready).mockResolvedValueOnce({...ready,id:'publication-1',status:'PENDING',canPublish:false});
  const view=mount();
  await screen.findByRole('button',{name:'Preview feedback'});
  expect(screen.queryByRole('button',{name:'Publish this feedback'})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'Preview feedback'}));
  expect(screen.getByText('<script>untrusted</script>')).toBeInTheDocument();expect(view.container.querySelector('script')).toBeNull();
  fireEvent.click(screen.getByRole('button',{name:'Publish this feedback'}));
  await waitFor(()=>expect(api).toHaveBeenCalledWith('/api/reviews/job-1/publication',{method:'POST'}));
  expect(await screen.findByText(/Queued for the publishing worker/)).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'Publish this feedback'})).not.toBeInTheDocument();
});
it('offers reconciliation only after an uncertain submission',async()=>{
  vi.mocked(api).mockResolvedValue({...ready,status:'UNKNOWN',canPublish:false,failureCode:'GITHUB_OUTCOME_UNKNOWN'});
  mount();fireEvent.click(await screen.findByRole('button',{name:'Check for existing review'}));
  await waitFor(()=>expect(api).toHaveBeenCalledWith('/api/reviews/job-1/publication/reconcile',{method:'POST'}));
  expect(screen.queryByRole('button',{name:'Publish this feedback'})).not.toBeInTheDocument();
});
