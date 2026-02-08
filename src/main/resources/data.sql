-- Demo API: receives JSON, routes to demo-flow
MERGE INTO FG_API (ID, DATA) KEY (ID) VALUES (
    'demo-api',
    '{
        "TARGET": {
            "": ["demo-flow"]
        },
        "BACKUP_PAYLOAD": false
    }'
);

-- Demo Flow: writes incoming items to a JSON file
MERGE INTO FG_FLOW (ID, DATA) KEY (ID) VALUES (
    'demo-flow',
    '{
        "ENTRY": "writer",
        "MODULE": {
            "writer": {
                "CONNECT": "file-output",
                "ACTION": "CREATE",
                "RULE": "writer-rule",
                "TARGET": "output.json"
            }
        },
        "RULE": {
            "writer-rule": {
                "name": "name",
                "value": "value"
            }
        }
    }'
);

-- Demo Datasource: file output directory
MERGE INTO FG_DATASOURCE (ID, DATA) KEY (ID) VALUES (
    'file-output',
    '{
        "CONNECTOR": "FILE",
        "URL": "./data/output"
    }'
);
