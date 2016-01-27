
def env = System.getenv()

releaseVersion = env['RELEASE_VERSION']
releaseBranch = "release-" + releaseVersion
releaseTag = releaseVersion
developmentVersion = env['DEVELOPMENT_VERSION']
releaseFromBranch = env['RELEASE_FROM_BRANCH']
defaultBuildBranch = "master"

git_cmd = "git"

def runCommand(strList) {
  print "[INFO] ( "
  if(strList instanceof List) {
    strList.each { print "${it} " }
  } else {
    print strList
  }
  println " )"

  def proc = strList.execute()
  proc.in.eachLine { line -> println line }
  proc.out.close()
  proc.waitFor()

  if (proc.exitValue()) {
    throw new RuntimeException("Failed to execute command with error: ${proc.getErrorStream()}")
  }
  return proc.exitValue()
}

def git(args) {
  runCommand(git_cmd + " " + args)
}

def checkIfLocalBranchExists(branchName) {
  try {
    git('rev-parse --verify ' + branchName)
    return true;
  } catch (all) {
    return false;
  }
}

def deleteLocalBranchIfExists(branchName) {
  if (checkIfLocalBranchExists(branchName)) {
    println "[INFO] Local branch " + branchName + " exits, removing."
    git('branch -D ' + branchName)
  } else {
    println "[INFO] Local branch " + branchName + " does not exist, continue."
  }
}

def deleteLocalTagIfExists(tagName) {
  if (checkIfLocalBranchExists(tagName)) {
    println "[INFO] Local tag " + tagName + " exits, removing."
    git('tag -d ' + tagName)
  } else {
    println "[INFO] Local tag " + tagName + " does not exist, continue."
  }
}

def verifyRemoteTagDoesntExist(tagName) {
  try {
    git('ls-remote --tags --exit-code origin ' + tagName)
    //git('ls-remote --heads --exit-code origin ' + releaseBranch)
  } catch (RuntimeException e) {
    println "[INFO] Tag " + tagName + " does not exist yet, continue."
    return null
  }
  throw new RuntimeException("Tag " + tagName + " already exists!")
}

def createReleaseBranch() {
  if (releaseFromBranch != defaultBuildBranch) {
    git('checkout ' + releaseFromBranch)
  }
  git('branch ' + releaseBranch)
  git('pull origin ' + releaseFromBranch)
}

def commitAndCheckoutReleaseBranch() {
  git('add .')
  runCommand(["git", "commit", "-m", "Development version updated to " + developmentVersion])
  git('checkout ' + releaseBranch)
}

def commitReleaseBranch() {
  git('add .')
  runCommand(["git", "commit", "-m", "Release version updated to " + releaseVersion])
  git("tag " + releaseTag + " " +releaseBranch)
}

def pushTagsAndBranches() {
  git('push origin ' + releaseFromBranch + ':' + releaseFromBranch)
  git('push origin ' + releaseBranch + ':' + releaseBranch + ' --tags')
}

def action = this.args[0]

if (action == 'verify-and-create-release-branch') {
  verifyRemoteTagDoesntExist(releaseTag)

  deleteLocalBranchIfExists(releaseBranch)
  deleteLocalTagIfExists(releaseTag)
  createReleaseBranch();
} else if (action == 'commit-current-and-checkout-release-branch') {
  commitAndCheckoutReleaseBranch();
} else if (action == 'commit-release-branch') {
  commitReleaseBranch();
} else if (action == 'after-build-success') {
  pushTagsAndBranches();
}
