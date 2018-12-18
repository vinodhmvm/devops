def stack_aws_id = ''
def stack_aws_secret = ''
def stack_aws_cred_name = ''
def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
    com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl.class,
    Jenkins.instance
)
def c = creds.findResult { it.id == id ? it : null }
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
    if (env.InstanceType == "LatestGen") {
        InstanceType == "t3"
    }
    else if (env.InstanceType == "PrevGen") {
        InstanceType == "t2"
    }
    
    withAWS(credentials: aws_cred_name){
               
        stage('Checkout') {
            //println "Environment: ${environment}\n Region: ${Region}"
            sh ('rm -rf ./*')
            git(
                url: "https://github.com/vinodhmvm/devops.git",
                credentialsId: 'githubcredentials',
                branch: "master"
            )
            sh ('ls -ltr')
        }
        
        dir("Packer/Centos7") {
            def var_file = ""
            configFileProvider([configFile(fileId: 'packer-var-v1', variable: 'VARIABLE_FILE')]) {
                sh "cat ${env.VARIABLE_FILE} > variables.json"
                sh "cat variables.json"
                sh "sed -i 's/PREFIX/${InstanceType}/g' variables.json"
                sh "sed -i 's/ENV/${Environment}/g' variables.json"
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

