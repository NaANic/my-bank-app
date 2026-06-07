{{/*
Common labels applied to every object.
*/}}
{{- define "gateway.labels" -}}
app.kubernetes.io/name: gateway
app.kubernetes.io/component: gateway
app.kubernetes.io/part-of: bank
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels (stable subset used by Service selector and Deployment matchLabels).
*/}}
{{- define "gateway.selectorLabels" -}}
app.kubernetes.io/name: gateway
app.kubernetes.io/component: gateway
{{- end -}}

{{/*
Fully qualified image reference. image.tag falls back to global.image.tag.
*/}}
{{- define "gateway.image" -}}
{{- $tag := .Values.image.tag | default .Values.global.image.tag -}}
{{- printf "%s:%s" .Values.image.repository $tag -}}
{{- end -}}
