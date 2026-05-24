{{- define "dal.name" -}}
distributed-audit-ledger
{{- end -}}

{{- define "dal.fullname" -}}
{{- printf "%s-%s" .Release.Name "dal" | trunc 63 | trimSuffix "-" -}}
{{- end -}}


{{- define "dal.namespace" -}}
{{- default .Release.Namespace .Values.namespaceOverride -}}
{{- end -}}

{{- define "dal.configName" -}}{{ include "dal.fullname" . }}-config{{- end -}}
{{- define "dal.secretsName" -}}{{ include "dal.fullname" . }}-secrets{{- end -}}
{{- define "dal.postgresName" -}}{{ include "dal.fullname" . }}-postgres{{- end -}}
{{- define "dal.zookeeperName" -}}{{ include "dal.fullname" . }}-zookeeper{{- end -}}
{{- define "dal.kafkaName" -}}{{ include "dal.fullname" . }}-kafka{{- end -}}
{{- define "dal.commandServiceName" -}}{{ include "dal.fullname" . }}-command-service{{- end -}}
{{- define "dal.eventStoreServiceName" -}}{{ include "dal.fullname" . }}-event-store-service{{- end -}}
{{- define "dal.auditWriterServiceName" -}}{{ include "dal.fullname" . }}-audit-writer-service{{- end -}}
{{- define "dal.queryServiceName" -}}{{ include "dal.fullname" . }}-query-service{{- end -}}
{{- define "dal.auditUiName" -}}{{ include "dal.fullname" . }}-audit-ui{{- end -}}
{{- define "dal.ingressName" -}}{{ include "dal.fullname" . }}-ingress{{- end -}}

