def evalValue(expression, path = null) {
	return sh( returnStdout: true,
		script: "mvn org.apache.maven.plugins:maven-help-plugin:evaluate -Dexpression=${ expression } -q -DforceStdout ${ path==null ? '' : ('-pl='+path) } | tail -1")
}

def getToolchainId(mod) {
	if(mod.hasTag('jdk-11'))
		return 'toolchain-openjdk-11';
	return 'toolchain-openjdk-1-8-0';
}

def installArtifact(mod, parent = null) {
	if(!mod.active()) {
		skipStage()
		return
	}
	def relPath = (parent == null ? '.' : mod.relPathFrom(parent))
	// get module metadata
	def groupId = mod.metadata().get('maven.groupId');
	def artifactId = mod.metadata().get('maven.artifactId');
	def version = mod.metadata().get('maven.version');
	echo "Building: ${ groupId }:${ artifactId }:${ version }"
	try {
		sh "mvn-dev -P ${ REPOS },${ getToolchainId(mod) },ci-install -pl=${ relPath }"
	} finally {
		def baseName = "${ artifactId }-${ version }"
		// create spec .pom in target/ path
		sh "cp -T '${ mod.path() }/pom.xml' '${ mod.path() }/target/${ baseName }.pom'"
		// archive artifacts
		dir(path: "${ mod.path() }/target") {
			sh 'ls -l'
			archiveArtifacts artifacts: "${ baseName }.pom", fingerprint: true
			if(mod.hasTag('pack-jar')) {
				archiveArtifacts artifacts: "${ baseName }*.jar", fingerprint: true
			}
		}
		// create signatures
		signArtifacts(artifacts: "${ baseName }*")
		// bundle artifacts + signatures
		bundleArtifacts( bundle: mod.id(), artifacts: "${ baseName }.pom*", metadata: [
			'groupId': groupId, 'artifactId': artifactId, 'version': version
		])
		for (test in [ false, true ]) {
			for (classifier in [ '', 'javadoc', 'sources' ]) {
				if(test)
					classifier = classifier=='' ? 'tests' : ('test-'+classifier)
				bundleArtifacts( bundle: mod.id(), artifacts: "${ baseName }${ classifier=='' ? '' : ('-'+classifier) }.jar*", metadata: [
					'groupId': groupId, 'artifactId': artifactId, 'version': version, 'classifier': classifier
				])
			}
		}
	}
}

def testArtifacts(mods, tag, toolchainId, testProfile, parent = null) {
	mods = mods.findAll({ it.hasTag(tag) })
	stage(tag) {
		if(mods.isEmpty()) {
			skipStage()
			return
		}

		def modPaths = mods.collect({ it.relPathFrom(parent) }).join(',');
		sh "mvn-dev -P ${ REPOS },${ toolchainId },ci-test-build -pl=${ modPaths }"
		sh "mvn-dev --fail-never -P ${ REPOS },${ toolchainId },ci-test-exec,${ testProfile } -pl=${ modPaths }"
		// check tests, archive reports in case junit flags errors
		junit '*/target/surefire-reports/*.xml'
		if(currentBuild.resultIsWorseOrEqualTo('UNSTABLE')) {
			archiveArtifacts artifacts: '*/target/surefire-reports/*.xml'
		}
		// clean up the test reports
		sh 'rm -R */target/surefire-reports/*'
	}
}

node( label: 'linux' ) {
	withModules {
		tool(name: 'maven-latest', type: 'maven')

		stage('Checkout SCM') {
			checkout(scm)
		}

		sh 'chmod +x $WORKSPACE/.build/*'
		env.setProperty('PATH+SCRIPTS', "${ env.WORKSPACE }/.build")
		env.GLOBAL_MAVEN_SETTINGS     = '/srv/jenkins/.m2/global-settings.xml'
		env.MAVEN_SETTINGS            = "${ env.WORKSPACE }/.mvn/settings.xml"
		env.MAVEN_TOOLCHAINS          = "${ env.WORKSPACE }/.mvn/toolchains.xml"
		if(env.BRANCH_NAME == 'master') {
			env.REPOS = 'repo-releases'
		} else {
			env.REPOS = 'repo-releases,repo-development'
		}

		stage('Initialize') {
			env.RESULT_PATH  = "${ WORKSPACE }/result/"
			env.ARCHIVE_PATH = "${ WORKSPACE }/archive/"
			sh "mkdir -p ${ RESULT_PATH }"
			sh "mkdir -p ${ ARCHIVE_PATH }"
			
			addModule( id: 'project',         path: '.',               name: 'Maven Project',                tags: [  ])
			addModule( id: 'bom',             path: 'bom',             name: 'Bill of Materials',            tags: [ 'bom' ])
			addModule( id: 'sbom',            path: 'sbom',            name: 'SBOM',                         tags: [ 'bom' ])
			addModule( id: 'api',             path: 'api',             name: 'Maven Repo [api]',             tags: [ 'build1', 'pack-jar', 'jdk-1.8.0' ])
			addModule( id: 'core',            path: 'core',            name: 'Maven Repo [core]',            tags: [ 'build2', 'pack-jar', 'jdk-1.8.0' ])
			addModule( id: 'client-core',     path: 'client-core',     name: 'Maven Repo [client-core]',     tags: [ 'build4', 'pack-jar', 'jdk-1.8.0' ])
			addModule( id: 'client-http',     path: 'client-http',     name: 'Maven Repo [client-http]',     tags: [ 'build5', 'pack-jar', 'jdk-1.8.0', 'test-live' ])
			addModule( id: 'server-core',     path: 'server-core',     name: 'Maven Repo [server-core]',     tags: [ 'build4', 'pack-jar', 'jdk-1.8.0' ])
			addModule( id: 'server-http',     path: 'server-http',     name: 'Maven Repo [server-http]',     tags: [ 'build6', 'pack-jar', 'jdk-1.8.0', 'test-live' ])
			addModule( id: 'proxy-core',      path: 'proxy-core',      name: 'Maven Repo [proxy-core]',      tags: [ 'build7', 'pack-jar', 'jdk-1.8.0', 'test-smoke', 'test-live' ])
			addModule( id: 'cache-caffeine',  path: 'cache-caffeine',  name: 'Maven Repo [cache:caffeine]',  tags: [ 'build3', 'pack-jar', 'jdk-11'   , 'test-smoke' ])
			addModule( id: 'validation-pgp',  path: 'validation-pgp',  name: 'Maven Repo [validation:pgp]',  tags: [ 'build3', 'pack-jar', 'jdk-1.8.0', 'test-smoke' ])
		}
		def parentMod = getModule(id: 'project')

		stage('Init Modules') {
			sshagent (credentials: ['RunedUniverse-Jenkins']) {
				perModule(failFast: true) {
					def mod = getModule();
					def relPath = mod.relPathFrom(parentMod);
					mod.metadata().put('maven.groupId', evalValue('project.groupId', relPath));
					mod.metadata().put('maven.artifactId', evalValue('project.artifactId', relPath));
					def version = evalValue('project.version', relPath);
					mod.metadata().put('maven.version', version);
					// check skip flag
					// if not skipped -> check if this version already exists!
					mod.activate(
						!mod.hasTag('skip') && sh(
								label: "check if git tag \"${ mod.id() }/v${ version }\" exists",
								returnStatus: true,
								script: "git ls-remote --tags --exit-code origin refs/tags/${ mod.id() }/v${ version } &>/dev/null"
							) != 0
					);
				}
			}
		}
		stage ('Info') {
			sh 'printenv | sort'
		}

		stage('Update Maven Repo') {
			echo 'purging local maven repository'
			sh "mvn-dev -P ${ REPOS } dependency:purge-local-repository -DactTransitively=false -DreResolve=false"

			echo 'caching validation dependencies'
			sh "mvn-dev -P ${ REPOS },ci-validate dependency:resolve-plugins dependency:resolve -U --fail-never"

			if(checkAllModules(match: 'all', active: false)) {
				echo 'skipping build dependency download » unused'
			} else {
				echo 'caching build dependencies'
				sh "mvn-dev -P ${ REPOS },ci-install dependency:resolve -U --fail-never"
			}
		}

		stage('Code Validation') {
			sh "mvn-dev -P ${ REPOS },ci-validate,license-apache2-approve,license-epl-v10-approve --fail-at-end -T1C"
		}

		bundleContext {
			stage('Install Maven Parent') {
				installArtifact( parentMod );
			}
			stage('Install - BOMs') {
				perModule(withTagIn: [ 'bom' ]) {
					installArtifact( getModule(), parentMod )
				}
			}

			stage('Build') {
				// note: for-loop must not use a tag that does not exist - yes it's a bug!
				for(int i = 1; i<=7; i++) {
					perModule(withTagIn: [ "build${ i }" ]) {
						installArtifact( getModule(), parentMod )
					}
				}
			}

			stage('Smoke Test') {
				def mods = getModules(withTags: [ 'test-smoke' ]);
				if(!mods.any({ it.active() })) {
					skipStage()
					return
				}

				testArtifacts(mods, 'jdk-1.8.0', 'toolchain-openjdk-1-8-0', 'test-smoke', parentMod);
				testArtifacts(mods, 'jdk-11',    'toolchain-openjdk-11',    'test-smoke', parentMod);
			}

			stage('Live Test') {
				def mods = getModules(withTags: [ 'test-live' ]);
				if(!mods.any({ it.active() })) {
					skipStage()
					return
				}

				testArtifacts(mods, 'jdk-1.8.0', 'toolchain-openjdk-1-8-0', 'test-live', parentMod);
				testArtifacts(mods, 'jdk-11',    'toolchain-openjdk-11',    'test-live', parentMod);
			}

			stage('Package Build Result') {
				if(checkAllModules(match: 'all', active: false)) {
					skipStage()
					return
				}
				dir(path: "${ env.RESULT_PATH }") {
					unarchive mapping: ['*':'.']
					sh 'ls -l'
					sh "tar -I \"pxz -9\" -cvf ${ ARCHIVE_PATH }artifacts.tar.xz *"
					sh "zip -9 ${ ARCHIVE_PATH }artifacts.zip *"
				}
				dir(path: "${ env.ARCHIVE_PATH }") {
					archiveArtifacts artifacts: '*', fingerprint: true
				}
			}

			stage('Deploy') {
				perModule {
					def mod = getModule();
					if(!mod.active()) {
						skipStage()
						return
					}
					// bundle info
					bundleInfo( bundle: mod.id(), metadata: true )
					// deploy to development repo
					stage('Develop'){
						deployArtifacts( bundle: mod.id(), repo: 'nexus-runeduniverse>maven-development' )
					}
					// deploy to release repo
					stage('Release') {
						if(currentBuild.resultIsWorseOrEqualTo('UNSTABLE') || env.BRANCH_NAME != 'master') {
							skipStage()
							return
						}
						deployArtifacts( bundle: mod.id(), repo: 'nexus-runeduniverse>maven-releases' )
						def groupId = mod.metadata().get('maven.groupId');
						def artifactId = mod.metadata().get('maven.artifactId');
						def version = mod.metadata().get('maven.version');
						sshagent (credentials: ['RunedUniverse-Jenkins']) {
							sh "git tag -a ${ mod.id() }/v${ version } -f -m '[artifact] ${ groupId }:${ artifactId }:${ version }'"
							sh "git push origin ${ mod.id() }/v${ version }"
						}
					}
					// merge bundles into default
					bundleMerge( source: mod.id() )
				}
				stage('Stage at Maven-Central') {
					if(currentBuild.resultIsWorseOrEqualTo('UNSTABLE') || env.BRANCH_NAME != 'master') {
						skipStage()
						return
					}
					deployArtifacts( repo: 'maven-central>net.runeduniverse' )
				}
			}
		}

		cleanWs()
	}
}
