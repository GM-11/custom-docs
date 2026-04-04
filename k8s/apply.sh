kubectl apply -f secrets.yaml
kubectl apply -f configmap.yaml
kubectl apply -f authentication/deployment.yaml
kubectl apply -f authentication/service.yaml
kubectl apply -f docmanager/deployment.yaml
kubectl apply -f docmanager/service.yaml
kubectl apply -f connection-manager/deployment.yaml
kubectl apply -f connection-manager/service.yaml

kubectl rollout restart deployment
