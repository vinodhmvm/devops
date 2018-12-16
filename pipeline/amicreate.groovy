def stack_aws_id = ''
def stack_aws_secret = ''
def stack_aws_cred_name = ''
node (label: 'jenkinsslave') {
    if (env.ENVIRONMENT == "prod") {
        aws_cred_name = 'prod_aws_access_credentials'
    }
    else if (env.environment == "qas") {
        aws_cred_name = 'qas_aws_access_credentials'
    }
    else { // dev, beta, beta2
        aws_cred_name = 'dev_aws_access_credentials'
    }
    
    withCredentials([
        [ $class: 'UsernamePasswordMultiBinding',credentialsId: aws_cred_name, usernameVariable: 'AWS_ID', passwordVariable: 'AWS_KEY'],
    ]){
        stack_aws_id = "${AWS_ID}"
        stack_aws_secret = "${AWS_KEY}"
            
        stage('Checkout') {
            //println "Environment: ${environment}\n Region: ${Region}"
            sh ('rm -rf ./*')
            git(
                url: "http://gitlab.iff.com/cloud-engineering/${JOB_NAME}.git",
                credentialsId: 'GitLabIFF',
                branch: "master"
            )
            sh ('ls -ltr')
        }
        
        dir("centos7") {
            def var_file = ""
            configFileProvider([configFile(fileId: 'packer-var-v1', variable: 'VARIABLE_FILE')]) {
                sh "cat ${env.VARIABLE_FILE} > variables.json"
                sh "cat variables.json"
                sh "sed -i 's/SOURCEAMI/${SourceAMI}/g' variables.json"
                sh "sed -i 's/PREFIX/${InstanceType}/g' variables.json"
                sh "sed -i 's/ENV/${environment}/g' variables.json"
                sh "cat variables.json"
            }
            
            stage('Plan AMI') {
                // Mark the code build 'plan'....
                sh """(
                    packer.io validate -var-file=./variables.json  -var 'aws_access_key=${AWS_ID}' -var 'aws_secret_key=${AWS_KEY}' packer.json; echo \$? > status 
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
            
            def create = false
            try {
                stage name: 'Packer: Create AMI', concurrency: 1
                input message: 'Create AMI?', ok: 'Create'
                create = true
            } catch (err) {
                println "Create AMI Discarded: ${env.JOB_NAME} - ${env.BUILD_NUMBER}"
                create = false
                currentBuild.result = 'UNSTABLE'
            }
            
            if (create) {
                
                if (fileExists("build.status")) {
                    sh "rm -f build.status"
                }
                sh """(
                    packer.io build -var-file=./variables.json  -var 'aws_access_key=${AWS_ID}' -var 'aws_secret_key=${AWS_KEY}' packer.json; echo \$? > build.status 
                )"""
            }
        }
    }

}

