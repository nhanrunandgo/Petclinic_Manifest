pipeline {
    agent any

    parameters {
        string(name: 'branch', defaultValue: 'main', description: 'Branch cần comment các file YAML')
    }

    environment {
        GIT_REPO = 'https://github.com/nhanrunandgo/Petclinic_Manifest.git'
    }

    stages {
        stage('Checkout Manifest Repo') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'petclinic_github', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                    script {
                        // Clone lại repo nếu thư mục không tồn tại
                        def repoDir = 'Petclinic_Manifest'
                        if (!fileExists(repoDir)) {
                            sh """
                                git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/nhanrunandgo/Petclinic_Manifest.git
                            """
                        } else {
                            // Nếu repo đã tồn tại, pull mới nhất
                            sh """
                                cd Petclinic_Manifest
                                git pull origin ${params.branch}
                            """
                        }
                    }
                }
            }
        }

        stage('Comment YAML Files') {
            steps {
                dir('Petclinic_Manifest/dev') {
                    script {
                        // Tìm tất cả file .yml và xử lý từng file
                        sh '''
                            find . -name "*.yml" | while read file; do
                                # Nếu file có dòng nào chưa bắt đầu bằng # thì mới comment
                                if grep -q '^[^#[:space:]]' "$file"; then
                                    echo "Commenting $file"
                                    sed -i '/^[^#]/s/^/#/' "$file"
                                else
                                    echo "Skipping $file (already commented)"
                                fi
                            done
                        '''
                    }
                }
            }
        }


        stage('Commit and Push Changes') {
            steps {
                dir('Petclinic_Manifest') {
                    withCredentials([usernamePassword(credentialsId: 'petclinic_github', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                        sh """
                            git remote set-url origin https://github.com/nhanrunandgo/Petclinic_Manifest.git
                            git config user.name "jenkins-bot"
                            git config user.email "jenkins-bot@nhanrunandgo.xyz"

                            git add .
                            # Check nếu có thay đổi thì mới commit
                            if ! git diff --cached --quiet; then
                                git commit -m "Commented out all YAML files in dev folder"
                                git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/nhanrunandgo/Petclinic_Manifest.git ${params.branch}
                            else
                                echo "No changes to commit. Skipping commit and push."
                            fi
                        """
                    }
                }
            }
        }
    }
}
