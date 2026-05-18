#!/usr/bin/env python3
import subprocess
import json
import sys

def run_graphql(query):
    result = subprocess.run(
        ['gh', 'api', 'graphql', '-f', f'query={query}'],
        capture_output=True,
        text=True
    )
    if result.returncode != 0:
        print(f"Error: {result.stderr}", file=sys.stderr)
        return None
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as e:
        print(f"JSON decode error: {e}", file=sys.stderr)
        print(f"Output: {result.stdout}", file=sys.stderr)
        return None

def get_open_threads():
    query = 'query { repository(owner:"igorsatsyuk", name:"distributed-audit-ledger") { pullRequest(number:129) { reviewThreads(first:100) { nodes { id isResolved } } } } }'
    data = run_graphql(query)
    if not data:
        return []

    try:
        threads = data['data']['repository']['pullRequest']['reviewThreads']['nodes']
        return [t['id'] for t in threads if not t['isResolved']]
    except (KeyError, TypeError) as e:
        print(f"Error parsing response: {e}", file=sys.stderr)
        print(f"Response: {data}", file=sys.stderr)
        return []

def resolve_thread(thread_id):
    mutation = f'''mutation {{
      resolveReviewThread(input: {{threadId: "{thread_id}"}}) {{
        thread {{
          id
          isResolved
        }}
      }}
    }}'''

    result = subprocess.run(
        ['gh', 'api', 'graphql', '-f', f'query={mutation}'],
        capture_output=True,
        text=True
    )

    if result.returncode != 0:
        print(f"Error resolving {thread_id}: {result.stderr}", file=sys.stderr)
        return False

    try:
        data = json.loads(result.stdout)
        if 'errors' in data and data['errors']:
            print(f"GraphQL error: {data['errors']}", file=sys.stderr)
            return False
        return True
    except json.JSONDecodeError:
        print(f"JSON decode error", file=sys.stderr)
        return False

def main():
    print("Getting open threads...")
    threads = get_open_threads()
    print(f"Found {len(threads)} open threads")

    for thread_id in threads:
        print(f"Resolving {thread_id}...")
        if resolve_thread(thread_id):
            print(f"  ✓ Resolved")
        else:
            print(f"  ✗ Failed to resolve")

    print("Done!")

if __name__ == '__main__':
    main()

