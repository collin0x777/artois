from abc import ABC, abstractmethod
from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import inspect
from time import sleep


class ArtoisServer(ABC, BaseHTTPRequestHandler):

    @abstractmethod
    def generate(self, context):
        pass

    def parse_generate_kwargs(self, json_obj):
        argspec = inspect.getfullargspec(self.generate)
        print(argspec)
        args = argspec.args[1:]
        kwargs = {}
        for arg in args:
            if arg in json_obj:
                kwargs[arg] = json_obj[arg]

        return kwargs

    def do_GET(self):
        if self.path == '/':
            self.path = '/index.html'
        try:
            file_to_open = open(self.path[1:]).read()
            self.send_response(200)
        except:
            file_to_open = "File not found"
            self.send_response(404)
        self.end_headers()
        self.wfile.write(bytes(file_to_open, 'utf-8'))

    def do_POST(self):
        if self.path == '/':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            json_obj = json.loads(post_data)

            if 'context' not in json_obj:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(bytes("Missing context field", 'utf-8'))
                return

            kwargs = self.parse_generate_kwargs(json_obj)
            print(kwargs)
            generated = self.generate(**kwargs)
            response = json.dumps({'context': generated})

            self.send_response(200)
            self.end_headers()
            self.wfile.write(bytes(response, 'utf-8'))

    @classmethod
    def start(cls, address='localhost', port=1366):
        server = HTTPServer((address, port), cls)
        print(f"Server started, access it at http://{address}:{port}/")
        server.serve_forever()


class ArtoisServerExample(ArtoisServer):
    def generate(self, context, batch_size=1):
        sleep(0.3)
        for i in range(batch_size):
            if len(context) == 0:
                next_char = 'a'
            else:
                next_char = chr(ord(context[-1]) + 1)

            context = context + next_char

        return context


ArtoisServerExample.start()
