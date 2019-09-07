#!/usr/bin/env python

"""Extend Python's built in HTTP server to save files

curl or wget can be used to send files with options similar to the following

  curl -X PUT --upload-file somefile.txt http://localhost:8000
  wget -O- --method=PUT --body-file=somefile.txt http://localhost:8000/somefile.txt

__Note__: curl automatically appends the filename onto the end of the URL so
the path can be omitted.

"""
import os
from time import sleep

try:
    import http.server as server
except ImportError:
    # Handle Python 2.x
    import SimpleHTTPServer as server
    
import random
import string

def randomString(stringLength=10):
    """Generate a random string of fixed length """
    letters = string.ascii_lowercase
    return ''.join(random.choice(letters) for i in range(stringLength))

print ("Random String is ", randomString(5) )
print ("Random String is ", randomString(10) )

class HTTPRequestHandler(server.SimpleHTTPRequestHandler):
    """Extend SimpleHTTPRequestHandler to handle PUT requests"""
    def do_PUT(self):
        """Save a file following a HTTP PUT request"""
        filename = os.path.basename(self.path)

        # Don't overwrite files
        if os.path.exists(filename):
            print('Overwritten')
#             self.send_response(409, 'Conflict')
#             self.end_headers()
#             reply_body = '"%s" already exists\n' % filename
#             self.wfile.write(reply_body.encode('utf-8'))
#             return

        file_length = int(self.headers['Content-Length'])
        with open(filename, 'wb') as output_file:
            output_file.write(self.rfile.read(file_length))
        self.send_response(201, 'Created')
        self.end_headers()
        sleep(3)
        reply_body = f'{randomString(8)}'
        self.wfile.write(reply_body.encode('utf-8'))
        
    def do_POST(self):
        """Save a file following a HTTP POST request"""
        filename = os.path.basename(self.path)

        # Don't overwrite files
        if os.path.exists(filename):
            print('Existing name -- no problemo')
            #self.send_response(409, 'Conflict')
            #self.end_headers()
            #reply_body = '"%s" already exists\n' % filename
            #self.wfile.write(reply_body.encode('utf-8'))
            #return

        file_length = int(self.headers['Content-Length'])
        with open(filename, 'wb') as output_file:
            output_file.write(self.rfile.read(file_length))
        self.send_response(201, 'Created')
        self.end_headers()
        sleep(3)
        reply_body = f'{randomString(8)}\n'
        self.wfile.write(reply_body.encode('utf-8'))

if __name__ == '__main__':
    server.test(HandlerClass=HTTPRequestHandler, port=8080)
