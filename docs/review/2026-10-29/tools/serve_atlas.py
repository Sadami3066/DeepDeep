"""Optional localhost preview. Only inventoried source files can be opened outside the report directory."""
from pathlib import Path
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
import html, json

OUT=Path(__file__).resolve().parents[1]
ROOT=OUT.parents[2]
allowed={f['path'] for f in json.loads((OUT/'evidence/source-inventory.json').read_text())['files']}

class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(OUT),**kwargs)
    def do_GET(self):
        parsed=urlparse(self.path)
        if parsed.path!='/source':return super().do_GET()
        rel=parse_qs(parsed.query).get('file',[''])[0]
        if rel not in allowed:return self.send_error(404)
        path=ROOT/rel
        if not path.is_file():return self.send_error(404)
        lines=path.read_text().splitlines()
        source='\n'.join(f'<span id="L{i}"><a href="#L{i}">{i:4}</a> {html.escape(line)}</span>' for i,line in enumerate(lines,1))
        body=f'<!doctype html><meta charset="utf-8"><title>{html.escape(rel)}</title><style>body{{margin:28px;font:14px/1.6 monospace}}pre{{white-space:pre-wrap;overflow-wrap:anywhere}}a{{color:#567}}span:target{{background:#fff0ba}}h1{{font:18px sans-serif}}</style><h1>{html.escape(rel)}</h1><p>只读源码 · 报告基准 bdbaa07 · <a href="/atlas.html">返回图览</a></p><pre>{source}</pre>'.encode()
        self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)

if __name__=='__main__':ThreadingHTTPServer(('127.0.0.1',8786),Handler).serve_forever()
