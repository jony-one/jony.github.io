package main

import (
	"fmt"
	"net"
)

func main() {
	a,_ := net.ParseMAC("6e:b0:7d:4e:6b:e5")
	fmt.Println(a.String())
	//fmt.Println("Hello")
	//fmt.Println(possibleBipartition(10,[][]int{{6,9},{1,3},{4,8},{5,6},{2,8},{4,7},{8,9},{2,5},{5,8},{1,2},{6,7},{3,10},{8,10},{1,5},{3,6},{1,10},{7,9},{4,10},{7,10},{1,4},{9,10},{4,6},{2,7},{6,8},{5,7},{3,8},{1,8},{1,7},{7,8},{2,4}}))
}
func possibleBipartition(n int, dislikes [][]int) bool {
	// 找出不在循环队列中的数字
	// [1,2,3,4]
	// [1,2,3,4]
	if len(dislikes) <= 1 {
		return true
	}


	arr1  := make([]int, n+1)
	arr1[0] = 0
	for i := 0; i < n; i++ {
		arr1[i+1]=i+1
	}

	for _, dislike := range dislikes {
		if arr1[dislike[0]] > 0 && arr1[dislike[1]] > 0 {
			arr1[dislike[0]] = 0
		}
	}
	i:=0
	for _, i3 := range arr1 {
		if i3 > 0  {
			i++
			if i >=2 {
				return true
			}
		}
	}
	return false
}
