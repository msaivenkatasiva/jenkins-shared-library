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
    parameters{
        booleanParam(name: 'deploy', defaultValue: false, description: 'Toggle this value')
    }
    
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

        stage('sonar scan'){
            environment {
                scannerHome = tool 'sonar-7.0' //refering scanner CLI
            }
            steps {
                script {
                    withSonarQubeEnv('sonar-7.0') { //referring sonar server
                        sh "${scannerHome}/bin/sonar-scanner"
                    }
                }
            }
        }

        stage("Quality Gate") {
            steps {
              timeout(time: 30, unit: 'MINUTES') {
                waitForQualityGate abortPipeline: true
              }
            }
        }

        stage('Nexus Artifact Upload'){
            steps {
                script{
                    nexusArtifactUploader(
                        nexusVersion: 'nexus3',
                        protocol: 'http',
                        nexusUrl: "${nexusUrl}",
                        groupId: 'com.${project}',
                        version: "${appVersion}",
                        repository: "${component}",
                        credentialsId: 'nexus-auth',
                        artifacts: [
                            [artifactId: "${component}",
                            classifier: '',
                            file: '${component}-' + "${appVersion}" + '.zip',
                            type: 'zip']
                        ]
                    )
                }
            }
        }
        stage('Deploy') {
            when{
                expression{
                    params.deploy
                }
            }
            steps{
                script{
                    def params = [
                    string(name: 'appVersion', value: "${appVersion}")
                    ]
                    build job: '${component}-deploy', parameters: params, propagate: false
                } 
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