{{/*
Common labels applied to every object.
*/}}
{{- define "transfer.labels" -}}
app.kubernetes.io/name: transfer
app.kubernetes.io/component: microservice
app.kubernetes.io/part-of: bank
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels (stable subset used by Service selector and Deployment matchLabels).
*/}}
{{- define "transfer.selectorLabels" -}}
app.kubernetes.io/name: transfer
app.kubernetes.io/component: microservice
{{- end -}}

{{/*
Fully qualified image reference. image.tag falls back to global.image.tag.
*/}}
{{- define "transfer.image" -}}
{{- $tag := .Values.image.tag | default .Values.global.image.tag -}}
{{- printf "%s:%s" .Values.image.repository $tag -}}
{{- end -}}
