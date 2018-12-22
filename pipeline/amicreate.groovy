def stack_aws_id = ''
def stack_aws_secret = ''
def stack_aws_cred_name = ''
def IType = ''
node (label: 'jenkinsslave') {
    if (env.Environment == "prod") {
        aws_cred_name = 'prod_aws_access_credentials'
    }
    else if (env.Environment == "qas") {
        aws_cred_name = 'aws_qas_account'
    }
    else { // dev, beta, beta2
        aws_cred_name = 'aws_dev_account'
    }
    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: aws_cred_name]]){
               
        stage('Checkout') {
            sh ('rm -rf ./*')
            git(
                url: "https://github.com/vinodhmvm/devops.git",
                credentialsId: 'githubcredentials',
                branch: "master"
            )
            sh ('ls -ltr')
        }
        if (env.LinuxFlavour == "Centos7") {    
            dir("packer/Centos7") {
            def var_file = ""
            configFileProvider([configFile(fileId: 'packer-variable-v1', variable: 'VARIABLE_FILE')]) {
                sh "cat ${env.VARIABLE_FILE} > variables.json"
                sh "cat variables.json"
                if (env.InstanceType == "LatestGen") {
                   IType = 't3'
                   sh "sed -i 's/PREFIX/$IType/g' variables.json"
                }
                else if (env.InstanceType == "PrevGen") {
                    IType = 't2'
                    sh "sed -i 's/PREFIX/$IType/g' variables.json"
                }
                sh "sed -i 's/ENV/${env.Environment}/g' variables.json"
                sh "cat variables.json"
                }
            }
        }
        else if (env.LinuxFlavour == "Centos6") {    
            dir("packer/Centos6") {
            def var_file = ""
            configFileProvider([configFile(fileId: 'packer-variable-v1', variable: 'VARIABLE_FILE')]) {
                sh "cat ${env.VARIABLE_FILE} > variables.json"
                sh "cat variables.json"
                if (env.InstanceType == "LatestGen") {
                   IType = 't3'
                   sh "sed -i 's/PREFIX/$IType/g' variables.json"
                }
                else if (env.InstanceType == "PrevGen") {
                    IType = 't2'
                    sh "sed -i 's/PREFIX/$IType/g' variables.json"
                }
                sh "sed -i 's/ENV/${env.Environment}/g' variables.json"
                sh "cat variables.json"
                }
            }
        }
        else if (env.LinuxFlavour == "SUSE12SP3") {    
            dir("packer/SUSE12SP3") {
            def var_file = ""
            configFileProvider([configFile(fileId: 'packer-variable-v1', variable: 'VARIABLE_FILE')]) {
                sh "cat ${env.VARIABLE_FILE} > variables.json"
                sh "cat variables.json"
                if (env.InstanceType == "LatestGen") {
                   IType = 't3'
                   sh "sed -i 's/PREFIX/$IType/g' variables.json"
                }
                else if (env.InstanceType == "PrevGen") {
                    IType = 't2'
                    sh "sed -i 's/PREFIX/$IType/g' variables.json"
                }
                sh "sed -i 's/ENV/${env.Environment}/g' variables.json"
                sh "cat variables.json"
                }
            }
        }          
        stage('Plan AMI') {
            if (env.LinuxFlavour == "Centos7") {    
                dir("packer/Centos7") {        
                    sh """(
                    /usr/sbin/packer validate -var-file=./variables.json packer.json; echo \$? > status 
                    )"""
                    def exitCode = readFile('status').trim()
                    echo "Packer AMI Plan Exit Code: ${exitCode}"
                    if (exitCode == "0") {
                        currentBuild.result = 'SUCCESS'
                    }
                    if (exitCode == "1") {
                        println "AMI Plan Failed: ${env.JOB_NAME} - ${env.BUILD_NUMBER}"
                        currentBuild.result = 'FAILURE'
                    }
                }
            }
        }
        stage('Build AMI') {
           if (env.LinuxFlavour == "Centos7") {
                dir("packer/Centos7") {
                    try {
                        if (fileExists("build.status")) {
                            sh "rm -f build.status"
                        }
                        sh """(
                        /usr/sbin/packer build -var-file=./variables.json packer.json; echo \$? > build.status 
                        )"""                  
                    }
                    catch (err) {
                        println "Create AMI Discarded: ${env.JOB_NAME} - ${env.BUILD_NUMBER}"
                        currentBuild.result = 'UNSTABLE'    
                    }
                }
            }
        }
    }

}

