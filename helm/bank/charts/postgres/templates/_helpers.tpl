{{/*
Resource name for PostgreSQL. Kept equal to global.postgres.host so the
Service DNS name matches what the microservices connect to.
*/}}
{{- define "postgres.fullname" -}}
{{- default "postgres" .Values.global.postgres.host -}}
{{- end -}}

{{/*
Common labels applied to every object.
*/}}
{{- define "postgres.labels" -}}
app.kubernetes.io/name: {{ include "postgres.fullname" . }}
app.kubernetes.io/component: database
app.kubernetes.io/part-of: bank
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels (stable subset used by Service selector and StatefulSet matchLabels).
*/}}
{{- define "postgres.selectorLabels" -}}
app.kubernetes.io/name: {{ include "postgres.fullname" . }}
app.kubernetes.io/component: database
{{- end -}}
