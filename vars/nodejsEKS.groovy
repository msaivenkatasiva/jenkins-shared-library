def call(Map configMap) {
    pipeline {
    agent {
        label 'AGENT-1'
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }
    environment {
        def appVersion = '' //variable declaration
        def nexusUrl = pipelineGlobals.nexusURL()
        region = pipelineGlobals.region()
        account_id = pipelineGlobals.account_id()
        component = configMap.get("component")
        project = configMap.get("project")
    }
    // parameters{
    //     booleanParam(name: 'deploy', defaultValue: false, description: 'Toggle this value')
    // }
    // if going for deployment, parameters are not needed !, instead of removing, i just commented
    
    stages {
        stage('read the version') {
            steps {
                script {
                def packageJson = readJSON file: 'package.json'
                appVersion = packageJson.version
                echo "application version: $appVersion"
                }
            }
        }

        stage('install dependencies') {
            steps {
                sh """
                 npm install
                 ls -ltr
                 echo "application version: $appVersion" 
                """
            }
        }
        stage('Build'){
            steps {
                sh """
                zip -q -r ${component}-${appVersion}.zip * -x Jenkinsfile -x ${component}-${appVersion}.zip
                ls -ltr
                """
            }
        }
        stage('Docker Build'){
            steps {
                sh """
                    aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${account_id}.dkr.ecr.${region}.amazonaws.com

                    docker build -t ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}-${component}:${appVersion} .

                    docker push ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}-${component}:${appVersion}
                """
            }
        }
        stage('Deploy'){
            steps {
                sh """
                    cd helm
                    sed -i 's/IMAGE_VERSION/${appVersion}/g' values.yaml
                    helm upgrade ${component} .
                """
            }
        }

        
    }
    post {
        always {
            echo 'I Will run always'
            deleteDir()
        }
        success {
            echo 'I will run when pipeline is success'
        }
        failure {
            echo 'i will run when pipeline is failure'
        }
    }
}
}