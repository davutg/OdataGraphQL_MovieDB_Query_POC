#!/usr/bin/env python3
"""Verify portable continuation/reports; optionally restart the Java 17 app."""
import argparse
import json
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request


def request(port, path, query=None):
    body = None if query is None else json.dumps({'query': query}).encode()
    req = urllib.request.Request('http://localhost:%d%s' % (port, path), data=body,
        headers={'Content-Type': 'application/json', 'X-Index-Policy': 'AUDIT'})
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, json.load(response), response.headers


def start(port):
    options = {'$top': 75, '$orderby': 'Id asc', '$filter': 'Year ge 2000',
               '$select': 'Id,Title', '$expand': 'Director($select=Id,Name)'}
    status, data, headers = request(port, '/odata/Movies?' + urllib.parse.urlencode(options))
    assert status == 200 and len(data['value']) == 50, (status, data)
    link = urllib.parse.urlsplit(data['@odata.nextLink'])
    path = link.path if link.path.startswith('/') else '/odata/' + link.path
    odata = (path + '?' + link.query, {row['Id'] for row in data['value']})
    report_id = headers['X-Request-Id']
    query = '{movies(first:75,filter:{yearFrom:2000}){nodes{id} pageInfo{endCursor}}}'
    status, data, _ = request(port, '/graphql', query)
    assert status == 200 and 'errors' not in data, (status, data)
    connection = data['data']['movies']
    assert len(connection['nodes']) == 50
    graphql = (connection['pageInfo']['endCursor'], {row['id'] for row in connection['nodes']})
    return odata, graphql, report_id


def finish(port, state):
    odata, graphql, report_id = state
    status, data, _ = request(port, odata[0])
    assert status == 200 and len(data['value']) == 25 and '@odata.nextLink' not in data, (status, data)
    assert not odata[1].intersection(row['Id'] for row in data['value'])
    assert all('Director' in row and 'Title' in row and 'Year' not in row for row in data['value'])
    query = '{movies(first:75,filter:{yearFrom:2000},after:%s){nodes{id} pageInfo{hasNextPage}}}' % json.dumps(graphql[0])
    status, data, _ = request(port, '/graphql', query)
    assert status == 200 and 'errors' not in data, (status, data)
    connection = data['data']['movies']
    assert len(connection['nodes']) == 25 and not connection['pageInfo']['hasNextPage']
    assert not graphql[1].intersection(row['id'] for row in connection['nodes'])
    status, report, _ = request(port, '/api/requests/' + report_id)
    assert status == 200 and report['requestId'] == report_id and report['plans'], (status, report)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--restart', action='store_true')
    args = parser.parse_args()
    results = []
    for source, destination in [(8082, 8080), (8080, 8081), (8081, 8082)]:
        state = start(source)
        finish(destination, state)
        results.append({'source': source, 'destination': destination,
                        'checks': ['odata-50-plus-25', 'graphql-50-plus-25', 'shared-report'], 'status': 'PASS'})
    token_path = state[0][0]
    split = urllib.parse.urlsplit(token_path)
    token = urllib.parse.parse_qs(split.query)['$skiptoken'][0].strip("'")
    parts = token.split('.')
    parts[-1] = ('A' if parts[-1][0] != 'A' else 'B') + parts[-1][1:]
    status, _, _ = request(8080, '/odata/Movies?' + urllib.parse.urlencode({'$skiptoken': '.'.join(parts)}))
    assert status == 422, status
    status, _, _ = request(8080, token_path + '&$top=500')
    assert status == 422, status
    results.append({'checks': ['tampering-rejected', 'budget-override-rejected'], 'status': 'PASS'})
    if args.restart:
        state = start(8080)
        subprocess.run(['docker', 'compose', 'restart', 'app'], check=True, stdout=subprocess.DEVNULL)
        deadline = time.monotonic() + 60
        while True:
            try:
                status, _, _ = request(8080, '/api/capabilities')
                if status == 200:
                    break
            except (OSError, ValueError):
                pass
            if time.monotonic() > deadline:
                raise RuntimeError('Restarted app did not become ready')
            time.sleep(1)
        finish(8080, state)
        results.append({'checks': ['odata-survives-restart', 'graphql-survives-restart', 'report-survives-restart'], 'status': 'PASS'})
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
