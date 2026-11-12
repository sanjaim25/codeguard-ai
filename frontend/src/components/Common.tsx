import type {ReactNode} from 'react';
import {AlertCircle, ArrowLeft, ArrowRight, Inbox, RefreshCw} from 'lucide-react';

export function Badge({value}: {value:string}) { return <span className={`badge badge-${value.toLowerCase()}`}><span className="status-dot"/>{value.replaceAll('_', ' ').toLowerCase()}</span>; }
export function PageHeading({eyebrow, title, description, actions}: {eyebrow?:string; title:string; description:string; actions?:ReactNode}) {
  return <div className="page-heading"><div>{eyebrow && <div className="eyebrow">{eyebrow}</div>}<h1>{title}</h1><p>{description}</p></div><div className="heading-actions">{actions}</div></div>;
}
export function Loading({label='Loading workspace'}: {label?:string}) { return <div role="status" aria-label={label} className="skeleton-group"><span className="sr-only">{label}</span><div className="skeleton skeleton-title"/><div className="skeleton-row">{[0,1,2].map(i => <div className="skeleton skeleton-card" key={i}/>)}</div><div className="skeleton skeleton-panel"/></div>; }
export function ErrorState({error, retry}: {error:unknown; retry:()=>void}) { return <div role="alert" className="state-panel"><AlertCircle size={32}/><h2>We couldn’t load this view</h2><p>{error instanceof Error ? error.message : 'Please try again.'}</p><button className="button" onClick={retry}><RefreshCw size={16}/>Try again</button></div>; }
export function EmptyState({title, description, action}: {title:string; description:string; action?:ReactNode}) { return <div className="state-panel"><Inbox size={34}/><h2>{title}</h2><p>{description}</p>{action}</div>; }
export function Pagination({page, totalPages, total, onChange}: {page:number; totalPages:number; total:number; onChange:(page:number)=>void}) {
  return <div className="pagination"><span>{total.toLocaleString()} results · Page {Math.min(page + 1, Math.max(totalPages, 1))} of {Math.max(totalPages, 1)}</span><div><button className="icon-button" aria-label="Previous page" disabled={page === 0} onClick={() => onChange(page - 1)}><ArrowLeft size={16}/></button><button className="icon-button" aria-label="Next page" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}><ArrowRight size={16}/></button></div></div>;
}
export function date(value:string) { return new Intl.DateTimeFormat(undefined, {month:'short', day:'numeric'}).format(new Date(value)); }
export function count(value:number) { return new Intl.NumberFormat().format(value); }
export function money(value:number|null) { return value === null ? 'Unavailable' : new Intl.NumberFormat('en-US', {style:'currency', currency:'USD', minimumFractionDigits:2, maximumFractionDigits:4}).format(value); }
