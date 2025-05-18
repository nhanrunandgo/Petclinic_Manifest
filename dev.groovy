pipeline {
    agent any

    parameters {
        string(name: 'admin_server_branch', defaultValue: 'main', description: 'Branch cho admin-server')
        string(name: 'api_gateway_branch', defaultValue: 'main', description: 'Branch cho api-gateway')
        string(name: 'config_server_branch', defaultValue: 'main', description: 'Branch cho config-server')
        string(name: 'customer_service_branch', defaultValue: 'main', description: 'Branch cho customer-service')
        string(name: 'discovery_server_branch', defaultValue: 'main', description: 'Branch cho discovery-server')
        string(name: 'genai_service_branch', defaultValue: 'main', description: 'Branch cho genai-service')
        string(name: 'vets_service_branch', defaultValue: 'main', description: 'Branch cho vets-service')
        string(name: 'visits_service_branch', defaultValue: 'main', description: 'Branch cho visits-service')
    }

    environment {
        DOCKERHUB_USERNAME = 'runandgo'
        SPRING_REPO = 'https://github.com/nhanrunandgo/spring-petclinic.git'
        MANIFEST_REPO = 'https://github.com/nhanrunandgo/Petclinic_Manifest.git'
    }

    stages {
        stage('Checkout Manifest Repo') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'petclinic_github', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                    script {
                        sh "rm -rf Petclinic_Manifest"
                        // Kiểm tra nếu thư mục Petclinic_Manifest đã tồn tại
                        def repoDir = 'Petclinic_Manifest'
                        if (!fileExists(repoDir)) {
                            // Nếu không tồn tại thì clone repo
                            sh """
                                git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/nhanrunandgo/Petclinic_Manifest.git
                            """
                        }
                    }
                }
            }
        }

        stage('Check and Uncomment YAML Files') {
            steps {
                dir('Petclinic_Manifest/dev') {
                    script {
                        // Kiểm tra và bỏ comment các file YAML nếu cần
                        sh '''
                            # Tìm tất cả file .yml và kiểm tra xem có bị comment không
                            for file in $(find . -name "*.yml"); do
                                if grep -q "^#" "$file"; then
                                    echo "Uncommenting file: $file"
                                    sed -i 's/^#//' "$file"
                                else
                                    echo "File $file is not commented, skipping"
                                fi
                            done
                        '''
                    }
                }
            }
        }

        stage('Update Manifests') {
            steps {
                dir('Petclinic_Manifest') {
                    script {
                        def services = [
                            [name: "admin-server", branch: params.admin_server_branch],
                            [name: "api-gateway", branch: params.api_gateway_branch],
                            [name: "config-server", branch: params.config_server_branch],
                            [name: "customers-service", branch: params.customer_service_branch],
                            [name: "discovery-server", branch: params.discovery_server_branch],
                            [name: "genai-service", branch: params.genai_service_branch],
                            [name: "vets-service", branch: params.vets_service_branch],
                            [name: "visits-service", branch: params.visits_service_branch]
                        ]

                        def commitMessages = []

                        services.each { service ->
                            echo "Updating ${service.name} from branch: ${service.branch}"

                            def imageTag = ""
                            if (service.branch == 'main') {
                                imageTag = 'latest'
                            } else {
                                imageTag = sh(script: "git ls-remote ${SPRING_REPO} ${service.branch} | awk '{print \$1}' | cut -c1-7", returnStdout: true).trim()
                                commitMessages.add("${service.name}:${imageTag}")

                            }

                            echo "Using image tag: ${imageTag}"

                            sh """
                                yq eval '.spec.template.spec.containers[0].image = "${DOCKERHUB_USERNAME}/spring-petclinic-${service.name}:${imageTag}"' -i dev/${service.name}/deployment.yml

                            """
                        }

                        sh """
                            echo "Update Docker tags: ${commitMessages.join(', ')}" > commit_message.txt
                        """
                    }
                }
            }
        }

        stage('Push Changes to GitHub') {
            steps {
                dir('Petclinic_Manifest') {
                    withCredentials([usernamePassword(credentialsId: 'petclinic_github', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                        sh """
                            git remote set-url origin https://github.com/nhanrunandgo/Petclinic_Manifest.git
                            git config user.name "jenkins-bot"
                            git config user.email "jenkins-bot@lptdevops.com"

                            git add .
                            git commit -F commit_message.txt || echo "Nothing to commit"
                            git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/nhanrunandgo/Petclinic_Manifest.git main

                        """
                    }
                }
            }
        }
    }
}
