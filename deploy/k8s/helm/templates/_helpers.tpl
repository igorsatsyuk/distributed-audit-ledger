{{- define "dal.name" -}}
distributed-audit-ledger
{{- end -}}

{{- define "dal.labels" -}}
app.kubernetes.io/name: {{ include "dal.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

