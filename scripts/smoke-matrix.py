#!/usr/bin/env python3
"""Check the three running Docker API/UI variants without changing movie data."""
import json
import urllib.error
import urllib.parse
import urllib.request


def request(base, path, query=None, mode='OFF'):
    body = None if query is None else json.dumps({'query': query}).encode()
    req = urllib.request.Request(base + path, data=body,
        headers={'Content-Type': 'application/json', 'X-Index-Policy': mode})
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, json.load(response), response.headers


results = []
for version, port in [('8', 8082), ('17', 8080), ('21', 8081)]:
    base = 'http://localhost:%d' % port
    status, caps, _ = request(base, '/api/capabilities')
    actual = caps['runtime']['javaVersion']
    assert status == 200 and actual.startswith('1.8.' if version == '8' else version + '.'), (version, caps)
    checks = []
    for protocol in ['odata', 'graphql']:
        if protocol == 'odata':
            path = '/odata/Movies?' + urllib.parse.urlencode({'$filter': "Genre eq 'RareGenre'", '$top': 5})
            query = None
        else:
            path = '/graphql'
            query = '{movies(first:5,filter:{genre:"RareGenre"}){nodes{id title}}}'
        status, data, headers = request(base, path, query, 'ENFORCE')
        assert status == 200 and 'errors' not in data, (version, protocol, data)
        rows = data['value'] if protocol == 'odata' else data['data']['movies']['nodes']
        assert len(rows) == 5
        _, trace, _ = request(base, '/api/requests/' + headers['X-Request-Id'])
        assert 'ix_movies_genre_id' in json.dumps(trace), (version, protocol, trace)
        checks.append(protocol + '-selective-index')
        if protocol == 'odata':
            path = '/odata/Movies?' + urllib.parse.urlencode({'$filter': "Title eq 'Inception'"})
            query = None
        else:
            path = '/graphql'
            query = '{movies(filter:{title:"Inception"}){nodes{id}}}'
        status, _, _ = request(base, path, query, 'ENFORCE')
        assert status == 422, (version, protocol, status)
        checks.append(protocol + '-enforce-rejection')
        if protocol == 'odata':
            path = '/odata/Movies?$top=75&$orderby=Id%20asc'
            status, first, _ = request(base, path)
            assert status == 200 and len(first['value']) == 50
            status, last, _ = request(base, '/odata/' + first['@odata.nextLink'])
            assert status == 200 and len(last['value']) == 25 and '@odata.nextLink' not in last
        else:
            query = '{movies(first:75){nodes{id} pageInfo{hasNextPage endCursor}}}'
            status, first, _ = request(base, '/graphql', query)
            connection = first['data']['movies']
            assert status == 200 and len(connection['nodes']) == 50
            cursor = connection['pageInfo']['endCursor']
            query = '{movies(first:75,after:%s){nodes{id} pageInfo{hasNextPage}}}' % json.dumps(cursor)
            status, last, _ = request(base, '/graphql', query)
            connection = last['data']['movies']
            assert status == 200 and len(connection['nodes']) == 25 and not connection['pageInfo']['hasNextPage']
        checks.append(protocol + '-50-plus-25')
    results.append({'module': 'app-java' + version, 'port': port, 'runtime': actual, 'checks': checks, 'status': 'PASS'})
print(json.dumps(results, indent=2))
