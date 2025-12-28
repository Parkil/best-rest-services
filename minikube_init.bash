#!/usr/bin/env bash

minikube start --profile=test --memory=10240 --cpus=4 --disk-size=20g --ports=8080:80 --ports=8443:443 --ports=30080:30080 --ports=30443:30443

INGRESS_IP=$(kubectl -n istio-system get service istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

MIKIKUBE_HOSTS="grafana.minikube.me tracing.minikube.me kiali.minikube.me prometheus.minikube.me minikube.me kibana.minikube.me elasticsearch.minikube.me mail.minikube.me health.minikube.me"

echo "$INGRESS_IP $MIKIKUBE_HOSTS" | sudo tee -a /etc/hosts

sudo minikube tunnel

minikube tunnel
