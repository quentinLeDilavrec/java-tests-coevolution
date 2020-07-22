const fs = require("fs")
const revT = fs.readFileSync('reversed.txt', 'utf8')
const topT = fs.readFileSync('top_projects.csv', 'utf8')
let maxS = 0
let maxF = 0
let maxW = 0
let maxL = 0
const fusion = {}
const rev = revT.split('\n')
    .map(x => x.split(' '))
    .map(x => [x[0], { stars: parseInt(x[1]), revs: x.slice(2) }])

rev.forEach(x => (maxS = Math.max(x[1].stars || 0, maxS), fusion[x[0]] = { ...x[1] }))

const top = topT.split('\n').slice(1)
    .map(x => x.split(','))
    .map(x => [x[0], { loc: parseInt(x[1]), forks: parseInt(x[2]), watchers: parseInt(x[3]) }])

top.forEach(x => (maxF = Math.max(x[1].forks || 0, maxF), maxL = Math.max(x[1].loc || 0, maxL), maxW = Math.max(x[1].watchers || 0, maxW), fusion[x[0]] = { ...fusion[x[0]], ...x[1] }))

// console.log(fusion)
// console.log(maxS,maxF,maxW,maxL)

Object.entries(fusion)
    .filter(x => x[1].revs && x[1].revs.length > 1 && x[1].forks!=undefined)
    .map(x => (x[1].weight = ((x[1].stars || 0) / maxS * 10) + ((x[1].forks || 0) / maxF) + ((x[1].watchers) || 0 / maxW * 10), x))
    .sort((b, a) => a[1].weight - b[1].weight)
    // .slice(0,10)
    .forEach(x => console.log(x[0] + " " + x[1].stars + " " + x[1].revs.join(" ")))
//.forEach(x=>console.log(x[0],x[1].stars,x[1].forks,x[1].watcher,x[1].loc))