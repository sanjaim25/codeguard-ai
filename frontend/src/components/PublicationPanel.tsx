import {useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {api} from '../api/client';
import {Badge,ErrorState} from './Common';

type Publication = {id:string|null;status:string;failureCode:string|null;githubUrl:string|null;canPublish:boolean;preview:{body:string;comments:{path:string;line:number;body:string}[]}|null};
export function PublicationPanel({jobId}:{jobId:string}) {
  const [expanded,setExpanded]=useState(false);
  const client=useQueryClient();
  const key=['publication',jobId];
  const query=useQuery({queryKey:key,queryFn:()=>api<Publication>(`/api/reviews/${jobId}/publication`),refetchInterval:q=>['PENDING','SENDING'].includes(q.state.data?.status??'')?5000:false});
  const publish=useMutation({mutationFn:()=>api<Publication>(`/api/reviews/${jobId}/publication`,{method:'POST'}),onSuccess:data=>client.setQueryData(key,data)});
  const reconcile=useMutation({mutationFn:()=>api<Publication>(`/api/reviews/${jobId}/publication/reconcile`,{method:'POST'}),onSuccess:data=>client.setQueryData(key,data)});
  if(query.isPending)return <section className="panel publication-panel" role="status">Loading publishing status…</section>;
  if(query.error)return <ErrorState error={query.error} retry={()=>void query.refetch()}/>;
  const publication=query.data;
  return <section className="panel publication-panel"><div className="review-summary-top"><div><h2>Publish to GitHub</h2><p>Send one advisory review with validated inline comments.</p></div><Badge value={publication.status}/></div>
    {publication.status==='NOT_REQUESTED'&&!publication.canPublish&&<p className="muted">Publishing requires repository write access, an enabled publishing policy and the current open commit. Demo reviews cannot be published.</p>}
    {publication.status==='PENDING'&&<p role="status">Queued for the publishing worker. Permissions and the commit will be checked before submission.</p>}
    {publication.status==='SENDING'&&<p role="status">Validating and submitting the GitHub review. Please wait.</p>}
    {publication.status==='UNKNOWN'&&<div className="notice warning">GitHub may have received this review. Check for the existing review below; this action will never submit it again.</div>}
    {publication.failureCode&&<p className="muted">Status detail: <code>{publication.failureCode}</code></p>}
    {publication.githubUrl&&<a className="button secondary" href={publication.githubUrl} target="_blank" rel="noopener noreferrer">View published review</a>}
    {publication.preview&&<><button className="button secondary small-button" onClick={()=>setExpanded(!expanded)} aria-expanded={expanded}>{expanded?'Hide feedback preview':'Preview feedback'}</button>{expanded&&<div className="publication-preview"><p className="muted">This preview uses the stored diff. Lines are revalidated against GitHub before publishing; unmappable findings become general observations. Markdown is shown as escaped source.</p><pre>{publication.preview.body.replace(/^<!--[^\n]*-->\n/,'')}</pre>{publication.preview.comments.map((comment,index)=><div key={index}><h3>{comment.path}:{comment.line}</h3><pre>{comment.body}</pre></div>)}</div>}</>}
    {publication.canPublish&&expanded&&<button className="button primary" disabled={publish.isPending} onClick={()=>publish.mutate()}>{publish.isPending?'Queuing…':'Publish this feedback'}</button>}
    {publication.status==='UNKNOWN'&&<button className="button secondary" disabled={reconcile.isPending} onClick={()=>reconcile.mutate()}>{reconcile.isPending?'Checking GitHub…':'Check for existing review'}</button>}
    {(publish.error||reconcile.error)&&<p role="alert" className="text-error">{(publish.error||reconcile.error)?.message}</p>}
  </section>;
}
