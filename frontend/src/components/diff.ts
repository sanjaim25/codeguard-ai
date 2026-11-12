export type DiffLine = {kind:'hunk'|'added'|'removed'|'context'|'meta'; text:string; oldLine:number|null; newLine:number|null};
export function parseDiff(patch:string):DiffLine[] {
  let oldLine=0,newLine=0;
  return patch.split('\n').filter((line,index,all)=>!(line===''&&index===all.length-1)).map(line=>{
    const hunk=/^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/.exec(line);
    if(hunk){oldLine=Number(hunk[1]);newLine=Number(hunk[2]);return {kind:'hunk',text:line,oldLine:null,newLine:null};}
    if(line.startsWith('+'))return {kind:'added',text:line.slice(1),oldLine:null,newLine:newLine++};
    if(line.startsWith('-'))return {kind:'removed',text:line.slice(1),oldLine:oldLine++,newLine:null};
    if(line.startsWith(' '))return {kind:'context',text:line.slice(1),oldLine:oldLine++,newLine:newLine++};
    return {kind:'meta',text:line,oldLine:null,newLine:null};
  });
}
